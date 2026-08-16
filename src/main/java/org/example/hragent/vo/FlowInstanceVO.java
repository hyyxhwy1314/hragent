package org.example.hragent.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
     * 申请人员工ID
     */
    private Long applyEmpId;

    /**
     * 申请人员工姓名
     */
    private String applyEmpName;

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
}
