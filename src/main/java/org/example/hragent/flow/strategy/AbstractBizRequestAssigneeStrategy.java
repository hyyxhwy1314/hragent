package org.example.hragent.flow.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.hragent.entity.Employee;
import org.example.hragent.exception.BusinessException;
import org.example.hragent.exception.ErrorCode;
import org.example.hragent.mapper.EmployeeMapper;
import org.example.hragent.service.AssigneeStrategy;

import java.util.HashMap;
import java.util.Map;

/**
 * 「业务单 + 直属上级 + HR」两级审批的通用审批人解析基类
 * <p>
 * 请假/证明流程由员工发起，bizId = 业务单ID（非员工ID），因此与
 * {@link AbstractLeaderHrAssigneeStrategy}（bizId=员工ID）不同：
 * 子类只需通过 {@link #resolveApplyEmpId} 从业务单解析出申请员工ID，
 * 公共部分（直属上级 + 首个 HR 角色员工）由本基类完成。
 */
@Slf4j
public abstract class AbstractBizRequestAssigneeStrategy implements AssigneeStrategy {

    protected final EmployeeMapper employeeMapper;

    protected AbstractBizRequestAssigneeStrategy(EmployeeMapper employeeMapper) {
        this.employeeMapper = employeeMapper;
    }

    @Override
    public Map<String, String> resolve(Long bizId, Long applyEmpId) {
        Map<String, String> result = new HashMap<>();

        Long empId = resolveApplyEmpId(bizId);
        Employee employee = employeeMapper.selectById(empId);
        BusinessException.throwIf(employee == null, ErrorCode.EMP_NOT_EXIST, "员工不存在");

        Long leaderId = employee.getLeaderId();
        BusinessException.throwIf(leaderId == null, ErrorCode.LEADER_NOT_EXIST, "领导不存在");
        result.put("approver_dept", String.valueOf(leaderId));

        Employee hr = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getRole, "HR")
                .last("LIMIT 1"));
        BusinessException.throwIf(hr == null, ErrorCode.FLOW_APPROVER_NOT_FOUND, "未找到 HR 角色员工");
        result.put("approver_hr", String.valueOf(hr.getId()));

        log.info("业务单审批人解析 bizId={}, empId={}, 直属上级={}, hr={}",
                bizId, empId, leaderId, hr.getId());
        return result;
    }

    /** 子类从业务单ID解析申请员工ID */
    protected abstract Long resolveApplyEmpId(Long bizId);
}
