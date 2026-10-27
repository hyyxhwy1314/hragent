package org.example.hragent.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.hragent.constant.FlowConstants;

/*
 * 流程实例

 */
@Data
@NoArgsConstructor
@AllArgsConstructor
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
     * 业务对象类型：EMPLOYEE 员工 / RESUME 简历 / LEAVE_REQUEST 请假单；
     * 为空表示按 flow_type 推断（兼容历史数据），自定义流程由发起时指定
     */
    @TableField("biz_type")
    private String bizType;

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

    /**
     * 解析业务对象类型：优先取 biz_type（自定义流程发起时指定），
     * 历史数据无 biz_type 时按 flow_type 推断，避免多处重复实现同一映射
     */
    public String resolvedBizType() {
        if (bizType != null) {
            return bizType;
        }
        if (flowType == null) {
            return FlowConstants.BIZ_TYPE_EMPLOYEE;
        }
        return switch (flowType) {
            case FlowConstants.FLOW_TYPE_ONBOARD -> FlowConstants.BIZ_TYPE_RESUME;
            case FlowConstants.FLOW_TYPE_LEAVE_REQUEST -> FlowConstants.BIZ_TYPE_LEAVE_REQUEST;
            case FlowConstants.FLOW_TYPE_CERTIFICATE -> FlowConstants.BIZ_TYPE_CERTIFICATE;
            default -> FlowConstants.BIZ_TYPE_EMPLOYEE;
        };
    }
}
