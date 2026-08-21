package org.example.hragent.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流程审批记录：每次审批完成时写入一条，轨迹查询时按 task_id 精确匹配。
 * <p>
 * action：1=通过 2=拒绝 3=转办
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_flow_approval")
public class FlowApproval extends BaseEntity {

    /** 业务流程实例 ID（t_flow_instance.id） */
    @TableField("flow_instance_id")
    private Long flowInstanceId;

    /** Flowable 任务 ID */
    @TableField("task_id")
    private String taskId;

    /** 节点名称 */
    @TableField("node_name")
    private String nodeName;

    /** 审批人 t_employee.id */
    @TableField("approver_emp_id")
    private Long approverEmpId;

    /** 审批动作：1=通过 2=拒绝 3=转办 */
    @TableField("action")
    private Integer action;

    /** 审批意见 */
    @TableField("comment")
    private String comment;
}
