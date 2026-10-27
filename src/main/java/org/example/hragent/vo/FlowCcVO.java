package org.example.hragent.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 抄送我的流程视图
 */
@Data
public class FlowCcVO {

    /** 抄送记录ID（t_flow_cc.id），用于标记已读 */
    private Long id;

    /** 业务流程实例ID */
    private Long flowInstanceId;

    /** 流程流水号 */
    private String flowNo;

    /** 流程类型 */
    private String flowType;

    /** 业务主键ID */
    private Long bizId;

    /** 申请人 t_employee.id */
    private Long applyEmpId;

    /** 申请人姓名（后端回填，避免前端全量拉取员工表映射） */
    private String applyEmpName;

    /** 业务对象展示名（后端回填，避免前端全量拉取简历表映射） */
    private String bizName;

    /** 流程当前状态 */
    private Integer flowStatus;

    /** 抄送节点名称 */
    private String nodeName;

    /** 是否已读 */
    private Boolean readFlag;

    /** 抄送时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}