package org.example.hragent.vo;
import lombok.Data;

/**
 * 流程实例返回视图对象
 */
@Data
public class FlowInstanceVO {

    /**
     * 主键ID
     */
    private Long id;

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
     * Flowable流程实例ID
     */
    private String flowableProcInstId;

    /**
     * 业务扩展JSON数据
     */
    private String bizJson;
}