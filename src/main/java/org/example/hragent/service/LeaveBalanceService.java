package org.example.hragent.service;

import org.example.hragent.entity.LeaveBalance;

import java.math.BigDecimal;

/**
 * 假期余额 Service：年度余额查询（懒初始化）与扣减。
 */
public interface LeaveBalanceService extends BaseService<LeaveBalance> {

    /**
     * 查询年度假期余额；无记录时按工龄规则懒初始化并落库。
     *
     * @param empId 员工ID
     * @param year  年度
     * @return 余额记录（totalDays 固定、usedDays 已用）
     */
    LeaveBalance getOrInit(Long empId, int year);

    /**
     * 扣减已用天数（审批通过后调用），余额不足返回 false。
     */
    boolean deduct(Long empId, int year, BigDecimal days);
}
