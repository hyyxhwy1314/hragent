package org.example.hragent.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 请假申请表：员工发起请假，直属上级→HR 两级审批，通过后扣减假期余额。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("t_leave_request")
public class LeaveRequest extends BaseEntity {

    /** 申请员工ID(t_employee.id) */
    @TableField("emp_id")
    private Long empId;

    /** 请假类型: 年假/事假/病假/调休/其他 */
    @TableField("leave_type")
    private String leaveType;

    /** 开始日期 */
    @TableField("start_date")
    private LocalDate startDate;

    /** 结束日期 */
    @TableField("end_date")
    private LocalDate endDate;

    /** 请假天数 */
    @TableField("leave_days")
    private BigDecimal leaveDays;

    /** 请假原因 */
    @TableField("reason")
    private String reason;

    /** 状态 0审批中 1通过 2拒绝 3撤销 */
    @TableField("status")
    private Integer status;

    /** 关联流程实例(t_flow_instance.id) */
    @TableField("flow_instance_id")
    private Long flowInstanceId;
}
