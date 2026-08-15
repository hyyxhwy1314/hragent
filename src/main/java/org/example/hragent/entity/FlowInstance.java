package org.example.hragent.entity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_flow_instance")
public class FlowInstance extends BaseEntity {

    /**
     * 业务流水号
     */
    @TableField("flow_no")
    private String flowNo;

    /**
     * 流程类型：PERFORMANCE/REGULAR/LEAVE 等
     */
    @TableField("flow_type")
    private String flowType;

    /**
     * 业务主键id
     */
    @TableField("biz_id")
    private Long bizId;

    /**
     * 申请人 t_employee.id
     */
    @TableField("apply_emp_id")
    private Long applyEmpId;

    /**
     * 业务流程状态
     */
    @TableField("flow_status")
    private Integer flowStatus;

    /**
     * Flowable原生流程实例id
     */
    @TableField("flowable_proc_inst_id")
    private String flowableProcInstId;

    /**
     * 业务扩展json
     */
    @TableField("biz_json")
    private String bizJson;
}