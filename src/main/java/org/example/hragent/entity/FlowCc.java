package org.example.hragent.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流程抄送记录：审批/发起时指定的抄送人，仅需知悉，不参与审批流转。
 * <p>
 * readFlag：0=未读 1=已读。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_flow_cc")
public class FlowCc extends BaseEntity {

    /** 业务流程实例 ID（t_flow_instance.id） */
    @TableField("flow_instance_id")
    private Long flowInstanceId;

    /** 抄送发生的节点名称 */
    @TableField("node_name")
    private String nodeName;

    /** 抄送人 t_employee.id */
    @TableField("cc_emp_id")
    private Long ccEmpId;

    /** 读取标记：0=未读 1=已读 */
    @TableField("read_flag")
    private Integer readFlag;
}