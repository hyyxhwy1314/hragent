package org.example.hragent.service.impl;

import org.example.hragent.mapper.EmployeeMapper;
import org.springframework.stereotype.Component;

/**
 * 转正流程审批人解析策略（直属上级 + HR）
 */
@Component
public class RegularAssigneeStrategy extends AbstractLeaderHrAssigneeStrategy {

    public RegularAssigneeStrategy(EmployeeMapper employeeMapper) {
        super(employeeMapper);
    }

    @Override
    public String supportProcessKey() {
        return "regular-process";
    }
}