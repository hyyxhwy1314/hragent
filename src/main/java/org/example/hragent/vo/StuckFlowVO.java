package org.example.hragent.vo;

import lombok.Data;

/**
 * 异常卡住的流程实例视图：用于异常巡检结果展示与告警。
 */
@Data
public class StuckFlowVO {

    /** 业务流程实例ID（t_flow_instance.id） */
    private Long flowInstanceId;

    /** 业务流水号 */
    private String flowNo;

    /** 流程类型 */
    private String flowType;

    /** 业务主键ID */
    private Long bizId;

    /** 申请人ID */
    private Long applyEmpId;

    /** 申请人姓名 */
    private String applyEmpName;

    /** 当前停留节点名称（多个以、分隔） */
    private String currentNode;

    /** 当前处理人ID（取最早未处理任务的处理人） */
    private Long assigneeEmpId;

    /** 当前处理人姓名 */
    private String assigneeName;

    /** 已停留时长（小时） */
    private Long pendingHours;
}