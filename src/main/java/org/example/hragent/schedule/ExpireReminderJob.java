package org.example.hragent.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.hragent.entity.Employee;
import org.example.hragent.entity.ExpireReminder;
import org.example.hragent.mapper.ExpireReminderMapper;
import org.example.hragent.service.EmailService;
import org.example.hragent.service.EmployeeService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 到期提醒定时任务：每日凌晨扫描即将到期的员工并邮件提醒。
 * <p>
 * 合同到期：提前 30 天提醒（在职/试用员工）；转正到期：试用期员工提前 7 天提醒。
 * 每次提醒写 t_expire_reminder（UNIQUE emp_id+remind_type+expire_date）去重，防止重复发送。
 * <p>
 * 扫描采用游标分页（按 id 递增、每批 {@link #BATCH_SIZE} 条），逐批处理而非一次性全量加载，
 * 降低长事务持锁时间与死锁风险。
 */
@Slf4j
@Component
public class ExpireReminderJob {

    /** 合同到期提醒提前天数 */
    private static final int CONTRACT_ADVANCE_DAYS = 30;
    /** 转正到期提醒提前天数 */
    private static final int REGULAR_ADVANCE_DAYS = 7;
    /** 在职状态：0离职 1在职 2试用 */
    private static final int STATUS_OFF = 0;
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_PROBATION = 2;
    /** 提醒类型 */
    private static final String TYPE_CONTRACT = "CONTRACT";
    private static final String TYPE_REGULAR = "REGULAR";
    /** 游标分页批大小 */
    private static final int BATCH_SIZE = 200;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final EmployeeService employeeService;
    private final ExpireReminderMapper expireReminderMapper;
    private final EmailService emailService;

    public ExpireReminderJob(EmployeeService employeeService,
                             ExpireReminderMapper expireReminderMapper,
                             EmailService emailService) {
        this.employeeService = employeeService;
        this.expireReminderMapper = expireReminderMapper;
        this.emailService = emailService;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void scanExpiringEmployees() {
        LocalDate today = LocalDate.now();
        int contractCount = scanContractExpire(today);
        int regularCount = scanRegularExpire(today);
        log.info("到期提醒扫描完成：合同到期提醒 {} 条，转正到期提醒 {} 条", contractCount, regularCount);
    }

    /**
     * 合同到期扫描：contract_end_date 落在 [today, today+30]
     */
    private int scanContractExpire(LocalDate today) {
        LocalDate end = today.plusDays(CONTRACT_ADVANCE_DAYS);
        return scanByCursor(
                () -> new LambdaQueryWrapper<Employee>()
                        .ne(Employee::getEmpStatus, STATUS_OFF)
                        .isNotNull(Employee::getContractEndDate)
                        .between(Employee::getContractEndDate, today, end),
                TYPE_CONTRACT,
                Employee::getContractEndDate,
                "劳动合同到期提醒",
                emp -> String.format("您好，您的劳动合同将于 %s 到期，请及时联系 HR 办理续签事宜。",
                        emp.getContractEndDate().format(DATE_FMT)),
                today);
    }

    /**
     * 转正到期扫描：试用期员工(emp_status=2)的 regular_date 落在 [today, today+7]
     */
    private int scanRegularExpire(LocalDate today) {
        LocalDate end = today.plusDays(REGULAR_ADVANCE_DAYS);
        return scanByCursor(
                () -> new LambdaQueryWrapper<Employee>()
                        .eq(Employee::getEmpStatus, STATUS_PROBATION)
                        .isNotNull(Employee::getRegularDate)
                        .between(Employee::getRegularDate, today, end),
                TYPE_REGULAR,
                Employee::getRegularDate,
                "转正到期提醒",
                emp -> String.format("您好，您的试用期预计于 %s 结束，届时将办理转正，请留意相关通知。",
                        emp.getRegularDate().format(DATE_FMT)),
                today);
    }

    /**
     * 游标分页扫描：按 id 递增、每批 {@link #BATCH_SIZE} 条逐批处理，避免一次性全量加载。
     *
     * @param baseWrapper 每批构建基础筛选条件（Supplier 保证每批拿到干净 wrapper）
     * @param expireDateOf 从员工取到期日期
     * @param contentOf    生成提醒邮件正文
     */
    private int scanByCursor(Supplier<LambdaQueryWrapper<Employee>> baseWrapper,
                             String remindType,
                             Function<Employee, LocalDate> expireDateOf,
                             String subject,
                             Function<Employee, String> contentOf,
                             LocalDate today) {
        int count = 0;
        long lastId = 0L;
        while (true) {
            LambdaQueryWrapper<Employee> wrapper = baseWrapper.get();
            wrapper.gt(Employee::getId, lastId)
                    .orderByAsc(Employee::getId)
                    .last("LIMIT " + BATCH_SIZE);
            List<Employee> batch = employeeService.list(wrapper);
            if (batch.isEmpty()) {
                break;
            }
            for (Employee emp : batch) {
                LocalDate expireDate = expireDateOf.apply(emp);
                if (expireDate == null) {
                    continue;
                }
                if (remindIfAbsent(emp, remindType, expireDate, today)) {
                    sendEmail(emp, subject, contentOf.apply(emp));
                    count++;
                }
            }
            lastId = batch.get(batch.size() - 1).getId();
            if (batch.size() < BATCH_SIZE) {
                break;
            }
        }
        return count;
    }

    /**
     * 去重：该员工对应类型与到期日已存在提醒记录则跳过（不发送不落库）
     */
    private boolean remindIfAbsent(Employee emp, String remindType, LocalDate expireDate, LocalDate remindDate) {
        LambdaQueryWrapper<ExpireReminder> check = new LambdaQueryWrapper<>();
        check.eq(ExpireReminder::getEmpId, emp.getId())
                .eq(ExpireReminder::getRemindType, remindType)
                .eq(ExpireReminder::getExpireDate, expireDate);
        if (expireReminderMapper.selectCount(check) > 0) {
            return false;
        }
        ExpireReminder reminder = new ExpireReminder();
        reminder.setEmpId(emp.getId());
        reminder.setRemindType(remindType);
        reminder.setExpireDate(expireDate);
        reminder.setRemindDate(remindDate);
        reminder.setSent(1);
        expireReminderMapper.insert(reminder);
        return true;
    }

    /**
     * 邮件发送：邮箱缺失或发送失败仅记录日志，不阻断扫描
     */
    private void sendEmail(Employee emp, String subject, String content) {
        if (emp.getEmail() == null || !emp.getEmail().contains("@")) {
            log.warn("员工邮箱不可用，跳过到期提醒邮件 empId={}, subject={}", emp.getId(), subject);
            return;
        }
        emailService.sendSimpleMail(emp.getEmail(), subject, content);
    }
}
