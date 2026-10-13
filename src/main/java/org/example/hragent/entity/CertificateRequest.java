package org.example.hragent.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 证明开具申请表：在职/收入证明申请，审批通过后生成证明内容并邮件发送。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("t_certificate_request")
public class CertificateRequest extends BaseEntity {

    /** 申请员工ID(t_employee.id) */
    @TableField("emp_id")
    private Long empId;

    /** 证明类型: 在职证明/收入证明 */
    @TableField("cert_type")
    private String certType;

    /** 用途说明 */
    @TableField("purpose")
    private String purpose;

    /** 状态 0审批中 1通过 2拒绝 3撤销 */
    @TableField("status")
    private Integer status;

    /** 审批通过后生成的证明内容 */
    @TableField("content")
    private String content;

    /** 关联流程实例(t_flow_instance.id) */
    @TableField("flow_instance_id")
    private Long flowInstanceId;
}
