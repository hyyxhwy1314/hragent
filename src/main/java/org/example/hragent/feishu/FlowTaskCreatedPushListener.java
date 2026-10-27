package org.example.hragent.feishu;

import lombok.extern.slf4j.Slf4j;
import org.example.hragent.utils.FlowableUtils;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.common.engine.api.delegate.event.FlowableEventType;
import org.flowable.task.api.Task;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * 审批任务创建事件的发布监听器。
 * <p>
 * 监听 Flowable {@code TASK_CREATED} 事件，对所有「已指定具体审批人」的用户任务发布进程内事件；
 * 实际卡片推送由 {@link FeishuApprovalPushService} 在业务事务提交后异步执行，
 * 既避免阻塞流程主链路，也确保推送时业务数据已可见。
 * <p>
 * 仅候选组、无 assignee 的任务不推送（无法定位到具体飞书账号）。
 */
@Slf4j
@Component
public class FlowTaskCreatedPushListener implements FlowableEventListener {

    private static final Collection<? extends FlowableEventType> TYPES =
            List.of(FlowableEngineEventType.TASK_CREATED);

    private final ApplicationEventPublisher eventPublisher;

    public FlowTaskCreatedPushListener(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void onEvent(FlowableEvent event) {
        if (!(event instanceof FlowableEntityEvent entityEvent)) {
            return;
        }
        if (!(entityEvent.getEntity() instanceof Task task)) {
            return;
        }
        Long approverEmpId = FlowableUtils.parseEmpId(task.getAssignee());
        if (approverEmpId == null) {
            // 未指定具体审批人（仅候选组）的任务不主动推送
            return;
        }
        eventPublisher.publishEvent(new ApprovalTaskCreatedEvent(
                task.getId(), approverEmpId, task.getProcessInstanceId(), task.getName()));
    }

    @Override
    public boolean isFailOnException() {
        return false;
    }

    @Override
    public boolean isFireOnTransactionLifecycleEvent() {
        return false;
    }

    @Override
    public String getOnTransaction() {
        return null;
    }

    @Override
    public Collection<? extends FlowableEventType> getTypes() {
        return TYPES;
    }
}
