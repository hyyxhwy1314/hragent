package org.example.hragent.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 假期余额表：年度假期余额，工龄满1年 5天/年，不满按入职月数/12×5 取整。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("t_leave_balance")
public class LeaveBalance extends BaseEntity {

    /** 员工ID(t_employee.id) */
    @TableField("emp_id")
    private Long empId;

    /** 年度 */
    @TableField("year")
    private Integer year;

    /** 年度总假期天数 */
    @TableField("total_days")
    private BigDecimal totalDays;

    /** 已使用天数 */
    @TableField("used_days")
    private BigDecimal usedDays;
}
