package org.example.hragent.feishu;

/**
 * 审批任务创建事件（进程内事件）。
 * <p>
 * 由 {@link FlowTaskCreatedPushListener} 在 Flowable {@code TASK_CREATED} 时发布，
 * 经 {@code @TransactionalEventListener(AFTER_COMMIT)} 在业务事务提交后再异步推送卡片，
 * 避免在流程发起事务未提交时读取不到业务单据/流程实例数据。
 *
 * @param taskId        Flowable 任务ID
 * @param approverEmpId 审批人员工ID
 * @param procInstId    Flowable 流程实例ID
 * @param taskName      节点名称（如「直属上级审批」「HR人事审核」）
 */
public record ApprovalTaskCreatedEvent(String taskId, Long approverEmpId,
                                       String procInstId, String taskName) {
}
