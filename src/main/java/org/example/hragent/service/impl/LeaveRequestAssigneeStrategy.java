package org.example.hragent.service.impl;

import org.example.hragent.constant.FlowConstants;
import org.example.hragent.entity.Employee;
import org.example.hragent.entity.LeaveRequest;
import org.example.hragent.exception.BusinessException;
import org.example.hragent.exception.ErrorCode;
import org.example.hragent.mapper.EmployeeMapper;
import org.example.hragent.mapper.LeaveRequestMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 请假申请流程审批人解析策略（业务单 → 申请员工 → 直属上级 + HR）
 * <p>
 * 此外解析「总监审批人」（申请员工直属上级的上级），供流程在请假天数 &gt; 5 天时
 * 自动路由到总监审批节点使用；总监缺失时回退 HR，避免审批人缺失导致流程卡住。
 */
@Component
public class LeaveRequestAssigneeStrategy extends AbstractBizRequestAssigneeStrategy {

    private final LeaveRequestMapper leaveRequestMapper;

    public LeaveRequestAssigneeStrategy(EmployeeMapper employeeMapper, LeaveRequestMapper leaveRequestMapper) {
        super(employeeMapper);
        this.leaveRequestMapper = leaveRequestMapper;
    }

    @Override
    public String supportProcessKey() {
        return FlowConstants.PROC_KEY_LEAVE_REQUEST;
    }

    @Override
    protected Long resolveApplyEmpId(Long bizId) {
        LeaveRequest request = leaveRequestMapper.selectById(bizId);
        BusinessException.throwIf(request == null, ErrorCode.DATA_NOT_FOUND, "请假单不存在");
        return request.getEmpId();
    }

    @Override
    public Map<String, String> resolve(Long bizId, Long applyEmpId) {
        Map<String, String> result = super.resolve(bizId, applyEmpId);
        result.put("approver_director", resolveDirector(bizId, result));
        return result;
    }

    /** 总监审批人：申请员工直属上级的上级；缺失时回退 HR */
    private String resolveDirector(Long bizId, Map<String, String> resolved) {
        LeaveRequest request = leaveRequestMapper.selectById(bizId);
        Long empId = request == null ? null : request.getEmpId();
        Employee employee = empId == null ? null : employeeMapper.selectById(empId);
        Long leaderId = employee == null ? null : employee.getLeaderId();
        Employee leader = leaderId == null ? null : employeeMapper.selectById(leaderId);
        Long directorId = leader == null ? null : leader.getLeaderId();
        return directorId == null ? resolved.get("approver_hr") : String.valueOf(directorId);
    }
}
