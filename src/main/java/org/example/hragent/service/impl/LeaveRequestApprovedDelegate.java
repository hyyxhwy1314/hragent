package org.example.hragent.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.hragent.entity.LeaveRequest;
import org.example.hragent.mapper.LeaveRequestMapper;
import org.example.hragent.service.LeaveBalanceService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 请假流程审批通过后：扣减假期余额并回写请假单状态。
 * <p>
 * 由 BPMN 中 serviceTask 通过 delegateExpression 调用，同步执行。
 * 余额不足时抛出异常回滚流程（发起时已提示余额，属边界保护）。
 */
@Slf4j
@Component("leaveRequestApprovedDelegate")
public class LeaveRequestApprovedDelegate implements JavaDelegate {

    private final LeaveRequestMapper leaveRequestMapper;
    private final LeaveBalanceService leaveBalanceService;

    public LeaveRequestApprovedDelegate(LeaveRequestMapper leaveRequestMapper,
                                        LeaveBalanceService leaveBalanceService) {
        this.leaveRequestMapper = leaveRequestMapper;
        this.leaveBalanceService = leaveBalanceService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Long bizId = (Long) execution.getVariable("bizId");
        log.info("请假审批通过，开始扣减余额 bizId={}", bizId);

        LeaveRequest request = leaveRequestMapper.selectById(bizId);
        if (request == null) {
            log.error("请假单不存在 bizId={}", bizId);
            throw new IllegalStateException("请假单不存在: " + bizId);
        }

        boolean ok = leaveBalanceService.deduct(request.getEmpId(),
                LocalDate.now().getYear(), request.getLeaveDays());
        if (!ok) {
            log.error("假期余额不足，请假审批通过但扣减失败 bizId={}, empId={}, days={}",
                    bizId, request.getEmpId(), request.getLeaveDays());
            throw new IllegalStateException("假期余额不足，无法完成请假扣减: " + bizId);
        }

        request.setStatus(1); // 通过
        leaveRequestMapper.updateById(request);
        log.info("请假审批通过并扣减成功 bizId={}, empId={}, days={}",
                bizId, request.getEmpId(), request.getLeaveDays());
    }
}
