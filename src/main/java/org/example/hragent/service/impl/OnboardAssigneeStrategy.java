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

/**
 * 入职流程审批人解析策略
 * <p>
 * 入职流程的 bizId 是简历ID（候选人尚未成为员工）。
 * <ul>
 *   <li>approver_hr：HR 角色（从 t_employee 按 role=HR 查询，取第一个）</li>
 *   <li>部门主管审批人：由 HR 发起时通过 bizJson 指定为流程变量 targetLeaderId，
 *       BPMN 中 ${targetLeaderId} 直接取值，无需策略解析</li>
 * </ul>
 */
@Slf4j
@Component
public class OnboardAssigneeStrategy implements AssigneeStrategy {

    private final EmployeeMapper employeeMapper;

    public OnboardAssigneeStrategy(EmployeeMapper employeeMapper) {
        this.employeeMapper = employeeMapper;
    }

    @Override
    public String supportProcessKey() {
        return "onboard-process";
    }

    /**
     * 解析审批人
     *
     * @param bizId     业务ID，即简历ID
     * @param applyEmpId 申请者ID
     * @return 审批人ID集合，key：审批人节点名称，value：审批人ID
     */
    @Override
    public Map<String, String> resolve(Long bizId, Long applyEmpId) {
        Map<String, String> result = new HashMap<>();

        // HR 审批人：按 role=HR 查询
        Employee hr = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getRole, "HR")
                .last("LIMIT 1"));
        BusinessException.throwIf(hr == null, ErrorCode.FLOW_APPROVER_NOT_FOUND, "未找到 HR 角色员工");
        result.put("approver_hr", String.valueOf(hr.getId()));

        log.info("入职流程审批人解析 bizId(resumeId)={}, applyEmpId={}, hr={}",
                bizId, applyEmpId, hr.getId());
        return result;
    }
}
