package org.example.hragent.agent.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.langchain4j.agent.tool.Tool;
import org.example.hragent.constant.FlowConstants;
import org.example.hragent.entity.CertificateRequest;
import org.example.hragent.entity.Employee;
import org.example.hragent.entity.ExpireReminder;
import org.example.hragent.entity.LeaveBalance;
import org.example.hragent.entity.LeaveRequest;
import org.example.hragent.flow.support.BizFlowStarter;
import org.example.hragent.mapper.ExpireReminderMapper;
import org.example.hragent.mapper.LeaveRequestMapper;
import org.example.hragent.service.CertificateRequestService;
import org.example.hragent.service.EmployeeService;
import org.example.hragent.service.LeaveBalanceService;
import org.example.hragent.service.LeaveRequestService;
import org.example.hragent.vo.FlowInstanceVO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * 员工自助工具：请假申请、假期余额/记录、证明开具、到期提醒、转正资格校验。
 */
@Component
public class HrSelfServiceTools implements HrToolSet {

    /** 请假超过该天数时增加总监审批节点（用于提示审批路径） */
    private static final BigDecimal DIRECTOR_APPROVAL_DAYS = BigDecimal.valueOf(5);

    private final EmployeeService employeeService;
    private final LeaveRequestService leaveRequestService;
    private final LeaveBalanceService leaveBalanceService;
    private final CertificateRequestService certificateRequestService;
    private final LeaveRequestMapper leaveRequestMapper;
    private final ExpireReminderMapper expireReminderMapper;
    private final BizFlowStarter bizFlowStarter;
    private final HrToolSupport support;

    public HrSelfServiceTools(EmployeeService employeeService,
                              LeaveRequestService leaveRequestService,
                              LeaveBalanceService leaveBalanceService,
                              CertificateRequestService certificateRequestService,
                              LeaveRequestMapper leaveRequestMapper,
                              ExpireReminderMapper expireReminderMapper,
                              BizFlowStarter bizFlowStarter,
                              HrToolSupport support) {
        this.employeeService = employeeService;
        this.leaveRequestService = leaveRequestService;
        this.leaveBalanceService = leaveBalanceService;
        this.certificateRequestService = certificateRequestService;
        this.leaveRequestMapper = leaveRequestMapper;
        this.expireReminderMapper = expireReminderMapper;
        this.bizFlowStarter = bizFlowStarter;
        this.support = support;
    }

    /**
     * 发起请假申请：创建请假单并提交直属上级→HR 两级审批。
     * 参数示例：为张三发起请假 2 天，请假类型 年假，开始日期 2026-10-08，结束日期 2026-10-09，原因 回家探亲
     */
    @Tool("为指定员工发起请假申请，参数需包含请假类型（年假/事假/病假/调休等）、开始日期和结束日期（格式 yyyy-MM-dd），可附请假原因；未指定员工则默认为当前登录人")
    public String applyLeaveRequest(String query) {
        try {
            Employee applicant = support.resolveApplicant(query);
            if (applicant == null) {
                return "未找到申请员工，请提供员工姓名或确认当前已登录。例如：为张三发起请假 2 天，开始日期 2026-10-08";
            }
            String leaveType = support.extractNonEmpty(query, "类型");
            if (leaveType == null) {
                return "请提供请假类型（年假/事假/病假/调休等），例如：请假类型 年假";
            }
            LocalDate startDate = support.extractDate(query, 1);
            LocalDate endDate = support.extractDate(query, 2);
            if (startDate == null) {
                return "请提供请假开始日期（格式 yyyy-MM-dd），例如：开始日期 2026-10-08";
            }
            if (endDate == null) {
                endDate = startDate;
            }
            if (endDate.isBefore(startDate)) {
                return "请假结束日期不能早于开始日期，请检查日期是否正确";
            }
            String reason = support.extractNonEmpty(query, "原因");
            BigDecimal days = BigDecimal.valueOf(ChronoUnit.DAYS.between(startDate, endDate) + 1);

            // 余额校验（不足仅提示，不阻断发起）
            int year = LocalDate.now().getYear();
            LeaveBalance balance = leaveBalanceService.getOrInit(applicant.getId(), year);
            BigDecimal remaining = balance.getTotalDays()
                    .subtract(balance.getUsedDays() == null ? BigDecimal.ZERO : balance.getUsedDays());
            String balanceTip = remaining.compareTo(days) < 0
                    ? String.format("（注意：假期余额剩余 %s 天，不足以覆盖本次 %s 天，审批通过后可能扣减失败）", remaining, days)
                    : "";

            LeaveRequest request = new LeaveRequest();
            request.setEmpId(applicant.getId());
            request.setLeaveType(leaveType);
            request.setStartDate(startDate);
            request.setEndDate(endDate);
            request.setLeaveDays(days);
            request.setReason(reason);
            request.setStatus(0);
            leaveRequestService.save(request);

            FlowInstanceVO vo = bizFlowStarter.start(FlowConstants.PROC_KEY_LEAVE_REQUEST, request.getId(),
                    Map.of("leaveType", leaveType,
                            "startDate", startDate.toString(),
                            "endDate", endDate.toString(),
                            "leaveDays", days,
                            "reason", reason == null ? "" : reason),
                    "请假");

            request.setFlowInstanceId(vo.getId());
            leaveRequestService.updateById(request);

            return String.format("已为员工 %s（工号 %s）发起请假申请，请假单ID %d，流程单号 %s。\n"
                            + "请假类型：%s | %s 至 %s，共 %s 天%s\n"
                            + "审批路径：直属上级%s → HR 人事审核 → 通过后自动扣减假期余额并邮件通知。",
                    applicant.getEmpName(), applicant.getEmpNo(), request.getId(), vo.getFlowNo(),
                    leaveType, startDate, endDate, days, balanceTip,
                    days.compareTo(DIRECTOR_APPROVAL_DAYS) > 0 ? " → 总监审批（请假超5天）" : "");
        } catch (Exception e) {
            return "发起请假申请失败：" + e.getMessage();
        }
    }

    /** 查询员工年度假期余额 */
    @Tool("查询员工年度假期余额（总额/已用/剩余），参数为员工姓名，未指定则默认为当前登录人")
    public String queryLeaveBalance(String query) {
        try {
            Employee emp = support.resolveApplicant(query);
            if (emp == null) {
                return "未找到员工，请提供员工姓名或确认当前已登录";
            }
            int year = LocalDate.now().getYear();
            LeaveBalance balance = leaveBalanceService.getOrInit(emp.getId(), year);
            BigDecimal used = balance.getUsedDays() == null ? BigDecimal.ZERO : balance.getUsedDays();
            BigDecimal remaining = balance.getTotalDays().subtract(used);
            return String.format("员工 %s（工号 %s）%d 年假期余额：\n"
                            + "- 年度总额：%s 天\n- 已使用：%s 天\n- 剩余：%s 天",
                    emp.getEmpName(), emp.getEmpNo(), year,
                    balance.getTotalDays(), used, remaining);
        } catch (Exception e) {
            return "查询假期余额失败：" + e.getMessage();
        }
    }

    /** 查询员工请假记录 */
    @Tool("查询员工的历史请假记录（含审批状态），参数为员工姓名，未指定则默认为当前登录人")
    public String queryMyLeaveRequests(String query) {
        try {
            Employee emp = support.resolveApplicant(query);
            if (emp == null) {
                return "未找到员工，请提供员工姓名或确认当前已登录";
            }
            List<LeaveRequest> list = leaveRequestMapper.selectList(
                    new LambdaQueryWrapper<LeaveRequest>()
                            .eq(LeaveRequest::getEmpId, emp.getId())
                            .orderByDesc(LeaveRequest::getId)
                            .last("LIMIT 20"));
            if (list.isEmpty()) {
                return String.format("员工 %s 暂无请假记录", emp.getEmpName());
            }
            StringBuilder sb = new StringBuilder("员工 ").append(emp.getEmpName()).append(" 的请假记录（共 ")
                    .append(list.size()).append(" 条）：\n");
            for (LeaveRequest r : list) {
                sb.append(String.format("- %s | %s 至 %s | %s 天 | 状态 %s%s\n",
                        r.getLeaveType(), r.getStartDate(), r.getEndDate(), r.getLeaveDays(),
                        support.leaveRequestStatusText(r),
                        r.getReason() == null ? "" : " | 原因：" + r.getReason()));
            }
            return sb.toString();
        } catch (Exception e) {
            return "查询请假记录失败：" + e.getMessage();
        }
    }

    /** 发起证明开具申请（在职证明/收入证明） */
    @Tool("为指定员工发起在职证明或收入证明开具申请，参数需包含证明类型（在职证明/收入证明），可附用途说明；未指定员工则默认为当前登录人")
    public String applyCertificateRequest(String query) {
        try {
            Employee applicant = support.resolveApplicant(query);
            if (applicant == null) {
                return "未找到申请员工，请提供员工姓名或确认当前已登录";
            }
            String certType = resolveCertificateType(query);
            if (certType == null) {
                return "请提供证明类型（在职证明或收入证明），例如：发起收入证明申请";
            }
            String purpose = support.extractNonEmpty(query, "用途");

            CertificateRequest request = new CertificateRequest();
            request.setEmpId(applicant.getId());
            request.setCertType(certType);
            request.setPurpose(purpose);
            request.setStatus(0);
            certificateRequestService.save(request);

            FlowInstanceVO vo = bizFlowStarter.start(FlowConstants.PROC_KEY_CERTIFICATE, request.getId(),
                    Map.of("certType", certType, "purpose", purpose == null ? "" : purpose), "证明");

            request.setFlowInstanceId(vo.getId());
            certificateRequestService.updateById(request);

            return String.format("已为员工 %s（工号 %s）发起%s申请，申请单ID %d，流程单号 %s。\n"
                            + "用途：%s\n审批路径：直属上级 → HR 人事审核 → 通过后自动生成证明内容并邮件发送。",
                    applicant.getEmpName(), applicant.getEmpNo(), certType, request.getId(), vo.getFlowNo(),
                    purpose == null ? "（未填写）" : purpose);
        } catch (Exception e) {
            return "发起证明开具申请失败：" + e.getMessage();
        }
    }

    /** 识别证明类型：收入证明 / 在职证明；未识别返回 null */
    private String resolveCertificateType(String query) {
        if (query.contains("收入证明")) {
            return "收入证明";
        }
        if (query.contains("在职证明") || query.contains("工作证明")) {
            return "在职证明";
        }
        return null;
    }

    /** 查询合同到期 / 转正到期提醒 */
    @Tool("查询合同到期或转正到期的提醒信息，参数可指定合同/转正维度，不指定则返回全部近期提醒")
    public String queryExpireReminders(String query) {
        try {
            boolean wantContract = query.contains("合同");
            boolean wantRegular = query.contains("转正");
            if (!wantContract && !wantRegular) {
                wantContract = true;
                wantRegular = true;
            }

            List<ExpireReminder> list = expireReminderMapper.selectList(
                    new LambdaQueryWrapper<ExpireReminder>()
                            .orderByDesc(ExpireReminder::getExpireDate)
                            .last("LIMIT 20"));
            if (list.isEmpty()) {
                return "当前暂无到期提醒记录，系统每日自动扫描合同/转正到期并生成提醒";
            }

            StringBuilder sb = new StringBuilder("近期到期提醒（共 ").append(list.size()).append(" 条）：\n");
            for (ExpireReminder r : list) {
                if ((wantContract && "CONTRACT".equals(r.getRemindType()))
                        || (wantRegular && "REGULAR".equals(r.getRemindType()))) {
                    Employee emp = employeeService.getById(r.getEmpId());
                    String empName = emp == null ? "员工#" + r.getEmpId() : emp.getEmpName();
                    sb.append(String.format("- %s | %s | 到期日期 %s | 提醒日期 %s\n",
                            empName, "CONTRACT".equals(r.getRemindType()) ? "合同到期" : "转正到期",
                            r.getExpireDate(), r.getRemindDate()));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "查询到期提醒失败：" + e.getMessage();
        }
    }

    /** 转正资格校验：校验员工是否为试用状态、转正日期是否到达 */
    @Tool("校验指定员工是否满足转正条件（试用状态、转正日期是否到达），参数为员工姓名，未指定则默认为当前登录人")
    public String checkRegularEligibility(String query) {
        try {
            Employee emp = support.resolveApplicant(query);
            if (emp == null) {
                return "未找到员工，请提供员工姓名或确认当前已登录。例如：校验张三是否可以转正";
            }
            LocalDate today = LocalDate.now();
            StringBuilder sb = new StringBuilder(String.format("转正资格校验：%s（工号 %s）\n", emp.getEmpName(), emp.getEmpNo()));
            sb.append(String.format("- 当前状态：%s\n", support.empStatusText(emp.getEmpStatus())));
            sb.append(String.format("- 入职日期：%s\n", emp.getEntryDate() == null ? "（未登记）" : emp.getEntryDate()));

            if (emp.getEmpStatus() == null || emp.getEmpStatus() != HrToolSupport.STATUS_PROBATION) {
                sb.append(String.format("结论：不满足转正条件 —— 员工当前为「%s」，仅试用期员工可发起转正。\n",
                        support.empStatusText(emp.getEmpStatus())));
                return sb.toString();
            }

            LocalDate regularDate = emp.getRegularDate();
            if (regularDate == null) {
                sb.append("结论：暂不可判定 —— 未登记转正日期，请先由 HR 补充试用期转正日期后再发起。\n");
                return sb.toString();
            }
            sb.append(String.format("- 转正日期：%s\n", regularDate));
            if (today.isBefore(regularDate)) {
                sb.append(String.format("结论：暂不满足转正条件 —— 距转正日期还有 %d 天，到期后可发起转正。\n",
                        ChronoUnit.DAYS.between(today, regularDate)));
            } else {
                sb.append("结论：满足转正条件 —— 试用期已满，可发起转正流程。\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "转正资格校验失败：" + e.getMessage();
        }
    }
}