package org.example.hragent.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.hragent.entity.Employee;
import org.example.hragent.exception.BusinessException;
import org.example.hragent.exception.ErrorCode;
import org.example.hragent.mapper.EmployeeMapper;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * 调岗流程通过后进行员工岗位变更的 JavaDelegate
 * <p>
 * 由 BPMN 中 serviceTask transfer_3_auto_transfer 通过 delegateExpression 调用。
 * 从流程变量 bizId（=员工ID）定位员工，并把 HR 发起时通过 bizJson 传入的
 * targetDeptName / targetPosition 写入该员工部门与岗位。
 */
@Slf4j
@Component("transferApprovedDelegate")
public class TransferApprovedDelegate implements JavaDelegate {

    private final EmployeeMapper employeeMapper;

    public TransferApprovedDelegate(EmployeeMapper employeeMapper) {
        this.employeeMapper = employeeMapper;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Long empId = (Long) execution.getVariable("bizId");
        log.info("调岗审批通过，开始更新员工岗位 empId={}", empId);

        Employee employee = employeeMapper.selectById(empId);
        BusinessException.throwIf(employee == null, ErrorCode.EMP_NOT_EXIST, "员工不存在");

        // 目标部门/岗位由发起方通过 bizJson 平铺为流程变量，Jackson 解析后数值类型不固定，用 String 安全取值
        Object targetDeptObj = execution.getVariable("targetDeptName");
        Object targetPosObj = execution.getVariable("targetPosition");
        if (targetDeptObj != null) {
            employee.setDeptName(String.valueOf(targetDeptObj));
        }
        if (targetPosObj != null) {
            employee.setPositionName(String.valueOf(targetPosObj));
        }

        // 调岗只改部门/岗位，不改变员工在职/试用状态，保持原 empStatus 不变
        employeeMapper.updateById(employee);

        log.info("员工调岗成功 empNo={}, empId={}, 新部门={}, 新岗位={}",
                employee.getEmpNo(), empId, employee.getDeptName(), employee.getPositionName());
    }
}