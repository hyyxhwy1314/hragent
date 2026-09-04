package org.example.hragent.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程任务视图
 */
@Data
public class TaskVO {

    /** Flowable 任务ID */
    private String taskId;

    /** Flowable 流程实例ID */
    private String processInstanceId;

    /** 业务流程实例ID（t_flow_instance.id） */
    private Long flowInstanceId;

    /** 节点名称 */
    private String taskName;

    /** 处理人员工ID */
    private Long assigneeEmpId;

    /** 处理人姓名 */
    private String assigneeName;

    /** 流程类型 */
    private String flowType;

    /** 任务创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /** 业务主键ID */
    private Long bizId;

    /** 申请人ID */
    private Long applyEmpId;
}
