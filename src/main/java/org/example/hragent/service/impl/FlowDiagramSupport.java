package org.example.hragent.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.hragent.entity.FlowInstance;
import org.example.hragent.exception.BusinessException;
import org.example.hragent.exception.ErrorCode;
import org.example.hragent.mapper.FlowInstanceMapper;
import org.example.hragent.utils.CurrentUserService;
import org.example.hragent.vo.FlowDiagramVO;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 流程运行态图支持：BPMN XML + 活动/已完成节点高亮
 */
@Slf4j
@Component
public class FlowDiagramSupport {

    private final FlowInstanceMapper flowInstanceMapper;
    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final FlowAccessGuard accessGuard;

    public FlowDiagramSupport(FlowInstanceMapper flowInstanceMapper,
                              RepositoryService repositoryService,
                              RuntimeService runtimeService,
                              HistoryService historyService,
                              FlowAccessGuard accessGuard) {
        this.flowInstanceMapper = flowInstanceMapper;
        this.repositoryService = repositoryService;
        this.runtimeService = runtimeService;
        this.historyService = historyService;
        this.accessGuard = accessGuard;
    }

    public FlowDiagramVO getDiagram(Long flowInstanceId) {
        FlowInstance flowInstance = flowInstanceMapper.selectById(flowInstanceId);
        BusinessException.throwIf(flowInstance == null, ErrorCode.FLOW_INSTANCE_NOT_FOUND);
        accessGuard.assertAccess(flowInstance, CurrentUserService.empId());

        String procInstId = flowInstance.getFlowableProcInstId();
        FlowDiagramVO vo = new FlowDiagramVO();
        vo.setActiveActivityIds(new ArrayList<>());
        vo.setFinishedActivityIds(new ArrayList<>());
        if (procInstId == null) {
            return vo;
        }

        // 解析流程定义ID：运行中取运行时实例，已结束取历史实例
        ProcessInstance running = runtimeService.createProcessInstanceQuery()
                .processInstanceId(procInstId).singleResult();
        String procDefId = resolveProcDefId(procInstId, running);
        if (procDefId == null) {
            return vo;
        }

        // BPMN XML 原始内容
        try (InputStream in = repositoryService.getProcessModel(procDefId)) {
            vo.setBpmnXml(in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("读取流程模型失败 procDefId={}", procDefId, e);
        }

        // 运行中节点
        if (running != null) {
            vo.setActiveActivityIds(runtimeService.getActiveActivityIds(procInstId));
        }

        // 已完成历史节点（去重，排除连线/网关等非节点可由前端自行忽略）
        List<HistoricActivityInstance> finished = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(procInstId)
                .finished()
                .list();
        vo.setFinishedActivityIds(finished.stream()
                .map(HistoricActivityInstance::getActivityId)
                .distinct()
                .collect(Collectors.toList()));
        return vo;
    }

    /** 解析流程定义ID：优先运行时实例，其次历史实例 */
    private String resolveProcDefId(String procInstId, ProcessInstance running) {
        if (running != null) {
            return running.getProcessDefinitionId();
        }
        HistoricProcessInstance his = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(procInstId).singleResult();
        return his == null ? null : his.getProcessDefinitionId();
    }
}
