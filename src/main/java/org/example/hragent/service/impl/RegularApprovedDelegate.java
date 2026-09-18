package org.example.hragent.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.hragent.entity.Employee;
import org.example.hragent.exception.BusinessException;
import org.example.hragent.exception.ErrorCode;
import org.example.hragent.mapper.EmployeeMapper;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 转正流程通过后进行员工转正落库的 JavaDelegate
 * <p>
 * 由 BPMN 中 serviceTask regular_3_auto_regular 通过 delegateExpression 调用。
 * 从流程变量 bizId（=员工ID）定位员工，写入转正日期并把试用状态改为在职。
 */
@Slf4j
@Component("regularApprovedDelegate")
public class RegularApprovedDelegate implements JavaDelegate {

    private final EmployeeMapper employeeMapper;

    public RegularApprovedDelegate(EmployeeMapper employeeMapper) {
        this.employeeMapper = employeeMapper;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Long empId = (Long) execution.getVariable("bizId");
        log.info("转正审批通过，开始更新员工状态 empId={}", empId);

        Employee employee = employeeMapper.selectById(empId);
        BusinessException.throwIf(employee == null, ErrorCode.EMP_NOT_EXIST, "员工不存在");

        employee.setRegularDate(LocalDate.now());
        employee.setEmpStatus(1); // 在职（正式员工）
        employeeMapper.updateById(employee);

        log.info("员工转正成功 empNo={}, empId={}, regularDate={}",
                employee.getEmpNo(), empId, employee.getRegularDate());
    }
}