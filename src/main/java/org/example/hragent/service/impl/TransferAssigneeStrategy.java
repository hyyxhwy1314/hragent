package org.example.hragent.service.impl;

import org.example.hragent.mapper.EmployeeMapper;
import org.springframework.stereotype.Component;

/**
 * 调岗流程审批人解析策略（直属上级 + HR）
 */
@Component
public class TransferAssigneeStrategy extends AbstractLeaderHrAssigneeStrategy {

    public TransferAssigneeStrategy(EmployeeMapper employeeMapper) {
        super(employeeMapper);
    }

    @Override
    public String supportProcessKey() {
        return "transfer-process";
    }
}