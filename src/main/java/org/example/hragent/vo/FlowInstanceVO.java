package org.example.hragent.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 流程实例返回视图对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlowInstanceVO {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 业务流水号
     */
    private String flowNo;

    /**
     * 流程类型
     */
    private String flowType;

    /**
     * 业务主键ID
     */
    private Long bizId;

    /**
     * 业务对象类型：EMPLOYEE/RESUME/LEAVE_REQUEST
     */
    private String bizType;

    /**
     * 申请人员工ID
     */
    private Long applyEmpId;

    /**
     * 申请人姓名（后端回填，避免前端全量拉取员工表映射）
     */
    private String applyEmpName;

    /**
     * 业务对象展示名（后端回填，避免前端全量拉取简历表映射）
     */
    private String bizName;

    /**
     * 流程状态
     */
    private Integer flowStatus;

    /**
     * Flowable流程实例ID
     */
    private String flowableProcInstId;

    /**
     * 业务扩展JSON数据
     */
    private String bizJson;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
