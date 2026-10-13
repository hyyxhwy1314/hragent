package org.example.hragent.flow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.hragent.constant.FlowConstants;
import org.example.hragent.entity.FlowApproval;
import org.example.hragent.entity.FlowInstance;
import org.example.hragent.mapper.FlowApprovalMapper;
import org.example.hragent.mapper.FlowInstanceMapper;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.common.engine.api.delegate.event.FlowableEventType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;

/**
 * Flowable 流程结束事件兜底同步
 * <p>
 * 背景：BPMN 中的自动节点（邮件发送 ServiceTask）配置了 flowable:async="true"，
 * 最后一次审批 complete 时异步作业尚未执行，运行时实例仍存活，
 * {@code completeTask} 里"实例销毁=流程结束"的同步判断会漏判，
 * 导致 t_flow_instance.flow_status 永久停留在"进行中"。
 * <p>
 * 本监听器在流程真正结束时（PROCESS_COMPLETED 等事件）兜底同步：
 * 状态仍为 RUNNING 时，按最后一次审批动作收敛为"通过/拒绝"；取消事件收敛为"撤回"。
 */
@Slf4j
@Component
public class FlowProcessEventListener implements FlowableEventListener {

    /** 关注的流程级事件：正常结束 / 终止结束事件结束 / 取消 */
    private static final Collection<? extends FlowableEventType> TYPES = Arrays.asList(
            FlowableEngineEventType.PROCESS_COMPLETED,
            FlowableEngineEventType.PROCESS_COMPLETED_WITH_TERMINATE_END_EVENT,
            FlowableEngineEventType.PROCESS_CANCELLED
    );

    @Autowired
    private FlowInstanceMapper flowInstanceMapper;
    @Autowired
    private FlowApprovalMapper flowApprovalMapper;

    @Override
    public void onEvent(FlowableEvent event) {
        FlowableEngineEventType type = (FlowableEngineEventType) event.getType();
        String procInstId = ((FlowableEngineEvent) event).getProcessInstanceId();
        try {
            if (type == FlowableEngineEventType.PROCESS_CANCELLED) {
                syncStatus(procInstId, FlowConstants.STATUS_CANCELED);
            } else {
                syncFinalStatus(procInstId);
            }
        } catch (Exception e) {
            // 兜底同步失败不影响 Flowable 引擎主流程，仅记录日志
            log.error("流程结束事件兜底同步失败 procInstId={}, type={}", procInstId, type, e);
        }
    }

    /**
     * 流程正常/终止结束：按最后一次审批动作收敛最终状态
     */
    private void syncFinalStatus(String procInstId) {
        FlowInstance flowInst = findByProcInstId(procInstId);
        if (flowInst == null || !isRunning(flowInst)) {
            return;
        }
        int newStatus = resolveFinalStatus(flowInst.getId());
        flowInst.setFlowStatus(newStatus);
        flowInstanceMapper.updateById(flowInst);
        log.info("流程结束事件兜底同步 procInstId={}, flowInstanceId={}, 最终状态={}",
                procInstId, flowInst.getId(), newStatus);
    }

    /**
     * 流程取消：收敛为"撤回"
     */
    private void syncStatus(String procInstId, int status) {
        FlowInstance flowInst = findByProcInstId(procInstId);
        if (flowInst == null || !isRunning(flowInst)) {
            return;
        }
        flowInst.setFlowStatus(status);
        flowInstanceMapper.updateById(flowInst);
        log.info("流程取消事件兜底同步 procInstId={}, flowInstanceId={}, 状态={}",
                procInstId, flowInst.getId(), status);
    }

    /**
     * 按最后一次审批动作判定：2=拒绝→驳回；其余（含无审批记录）→通过
     */
    private int resolveFinalStatus(Long flowInstanceId) {
        FlowApproval last = flowApprovalMapper.selectOne(
                new LambdaQueryWrapper<FlowApproval>()
                        .eq(FlowApproval::getFlowInstanceId, flowInstanceId)
                        .orderByDesc(FlowApproval::getId)
                        .last("LIMIT 1"));
        if (last != null && last.getAction() != null && last.getAction() == 2) {
            return FlowConstants.STATUS_REJECTED;
        }
        return FlowConstants.STATUS_APPROVED;
    }

    private boolean isRunning(FlowInstance flowInst) {
        return flowInst.getFlowStatus() != null
                && flowInst.getFlowStatus() == FlowConstants.STATUS_RUNNING;
    }

    private FlowInstance findByProcInstId(String procInstId) {
        return flowInstanceMapper.selectOne(
                new LambdaQueryWrapper<FlowInstance>()
                        .eq(FlowInstance::getFlowableProcInstId, procInstId));
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
