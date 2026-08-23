package org.example.hragent.service;

import java.util.Map;

/**
 * 审批人解析器
 * <p>
 * 根据流程定义 key 与业务上下文，返回所有审批人变量（key=变量名，value=审批人员工ID字符串）。
 * 变量名将作为 BPMN 中的 ${approver_xxx} 表达式。
 */
public interface AssigneeResolver {

    /**
     * 解析审批人
     *
     * @param processKey 流程定义 key，如 onboard-process
     * @param bizId      业务主键ID（如员工ID）
     * @param applyEmpId 申请人员工ID
     * @return 审批人变量映射，如 {approver_hr=1001, approver_dept_leader=2002}
     */
    Map<String, String> resolve(String processKey, Long bizId, Long applyEmpId);
}
