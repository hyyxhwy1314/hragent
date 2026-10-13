package org.example.hragent.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.hragent.entity.Employee;
import org.example.hragent.entity.LeaveBalance;
import org.example.hragent.mapper.EmployeeMapper;
import org.example.hragent.mapper.LeaveBalanceMapper;
import org.example.hragent.service.LeaveBalanceService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 假期余额 Service 实现
 * <p>
 * 余额规则（轻量单池）：工龄满 1 年 5 天/年，不满按入职月数/12×5 向下取整；
 * 首次查询或扣减时懒初始化并落库，无需年度重置任务。
 * 缓存：读缓存 leaveBalance（unless 空），扣减清空整个缓存（项目规范）。
 */
@Slf4j
@Service
public class LeaveBalanceServiceImpl extends BaseServiceImpl<LeaveBalanceMapper, LeaveBalance>
        implements LeaveBalanceService {

    /** 每年假期天数基数 */
    private static final BigDecimal ANNUAL_DAYS = BigDecimal.valueOf(5);

    private final EmployeeMapper employeeMapper;

    public LeaveBalanceServiceImpl(EmployeeMapper employeeMapper) {
        this.employeeMapper = employeeMapper;
    }

    @Override
    @Cacheable(value = "leaveBalance", key = "#empId + '-' + #year", unless = "#result == null")
    public LeaveBalance getOrInit(Long empId, int year) {
        LeaveBalance balance = this.lambdaQuery()
                .eq(LeaveBalance::getEmpId, empId)
                .eq(LeaveBalance::getYear, year)
                .one();
        if (balance != null) {
            return balance;
        }
        // 懒初始化：按工龄规则计算年度额度
        LeaveBalance created = new LeaveBalance();
        created.setEmpId(empId);
        created.setYear(year);
        created.setTotalDays(calcTotalDays(empId));
        created.setUsedDays(BigDecimal.ZERO);
        this.save(created);
        log.info("假期余额懒初始化 empId={}, year={}, totalDays={}", empId, year, created.getTotalDays());
        return created;
    }

    @Override
    @CacheEvict(value = "leaveBalance", allEntries = true)
    public boolean deduct(Long empId, int year, BigDecimal days) {
        LeaveBalance balance = getOrInit(empId, year);
        if (balance.getUsedDays() == null) {
            balance.setUsedDays(BigDecimal.ZERO);
        }
        BigDecimal remaining = balance.getTotalDays().subtract(balance.getUsedDays());
        if (remaining.compareTo(days) < 0) {
            log.warn("假期余额不足 empId={}, year={}, 剩余={}, 需扣={}", empId, year, remaining, days);
            return false;
        }
        // 条件更新防超扣：used_days + days <= total_days
        boolean ok = this.update(new LambdaUpdateWrapper<LeaveBalance>()
                .eq(LeaveBalance::getId, balance.getId())
                .eq(LeaveBalance::getUsedDays, balance.getUsedDays())
                .setSql("used_days = used_days + " + days));
        if (ok) {
            log.info("假期扣减成功 empId={}, year={}, days={}", empId, year, days);
        }
        return ok;
    }

    /** 工龄规则计算年度额度：满1年 5天，不满按入职月数/12×5 向下取整 */
    private BigDecimal calcTotalDays(Long empId) {
        Employee emp = employeeMapper.selectById(empId);
        if (emp == null || emp.getEntryDate() == null) {
            return BigDecimal.ZERO;
        }
        long months = ChronoUnit.MONTHS.between(emp.getEntryDate(), LocalDate.now());
        if (months < 0) {
            return BigDecimal.ZERO;
        }
        if (months >= 12) {
            return ANNUAL_DAYS;
        }
        // 不满一年：按入职月数比例向下取整
        int days = (int) Math.floor(months * ANNUAL_DAYS.doubleValue() / 12.0);
        return BigDecimal.valueOf(days);
    }
}
