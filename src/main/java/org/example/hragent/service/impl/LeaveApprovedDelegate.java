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

@Slf4j
@Component("leaveApprovedDelegate")
public class LeaveApprovedDelegate implements JavaDelegate {

    private final EmployeeMapper employeeMapper;

    public LeaveApprovedDelegate(EmployeeMapper employeeMapper) {
        this.employeeMapper = employeeMapper;
    }
    @Override
    public void execute(DelegateExecution execution) {
        Long empId = (Long) execution.getVariable("bizId");
        log.info("离职审批通过，开始修改员工记录 empId={}", empId);

        Employee employee = employeeMapper.selectById(empId);
        BusinessException.throwIf(employee == null, ErrorCode.EMP_NOT_EXIST , "员工不存在");
        
        employee.setEmpStatus(0);   // 标记离职（0=离职，1=在职，2=试用）
        employee.setLeaveDate(LocalDate.now()); // 离职日期

        employeeMapper.updateById(employee);
        log.info("员工记录修改成功 empNo={}, empId={}",
                employee.getEmpNo(), empId);
    }
}
