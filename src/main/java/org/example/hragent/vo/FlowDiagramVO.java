package org.example.hragent.vo;

import lombok.Data;

import java.util.List;

/**
 * 流程运行态图视图：BPMN XML + 活动节点高亮信息
 * <p>
 * 供前端 bpmn-js Viewer 渲染，并对已完成/进行中节点着色标记。
 */
@Data
public class FlowDiagramVO {

    /** 流程定义 BPMN XML 内容 */
    private String bpmnXml;

    /** 当前处于活动（待处理）状态的节点ID列表 */
    private List<String> activeActivityIds;

    /** 已完成的历史节点ID列表 */
    private List<String> finishedActivityIds;
}