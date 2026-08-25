package org.example.hragent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.hragent.entity.Employee;
import org.example.hragent.exception.BusinessException;
import org.example.hragent.exception.ErrorCode;
import org.example.hragent.mapper.EmployeeMapper;
import org.example.hragent.service.AssigneeStrategy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class LeaveAssigneeStrategy implements AssigneeStrategy {

    private final EmployeeMapper employeeMapper;
    public LeaveAssigneeStrategy(EmployeeMapper employeeMapper) {
        this.employeeMapper = employeeMapper;
    }

    @Override
    public String supportProcessKey() {
        return "leave-process";
    }

    @Override
    public Map<String, String> resolve(Long bizId, Long applyEmpId) {
        Map<String, String> result = new HashMap<>();

        // 离职流程的 bizId 就是员工ID
        Employee employee = employeeMapper.selectById(bizId);
        BusinessException.throwIf(employee == null, ErrorCode.EMP_NOT_EXIST , "员工不存在");

        Long leaderId = employee.getLeaderId();
        BusinessException.throwIf(leaderId == null, ErrorCode.LEADER_NOT_EXIST , "领导不存在");
        result.put("approver_dept", String.valueOf(leaderId));

        Employee hr = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getRole, "HR")
                .last("LIMIT 1"));
        BusinessException.throwIf(hr == null, ErrorCode.FLOW_APPROVER_NOT_FOUND, "未找到 HR 角色员工");
        result.put("approver_hr", String.valueOf(hr.getId()));

        log.info("离职流程审批人解析 bizId(empId)={}, applyEmpId={}, 直属上级={}, hr={}",
                bizId, applyEmpId, leaderId, hr.getId());

        return result;
    }
}
