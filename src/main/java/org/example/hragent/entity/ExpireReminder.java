package org.example.hragent.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 到期提醒记录表：定时任务扫描合同/转正到期生成的提醒记录，UNIQUE 去重防止重复发送。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("t_expire_reminder")
public class ExpireReminder extends BaseEntity {

    /** 员工ID(t_employee.id) */
    @TableField("emp_id")
    private Long empId;

    /** 提醒类型: CONTRACT(合同到期)/REGULAR(转正到期) */
    @TableField("remind_type")
    private String remindType;

    /** 到期日期 */
    @TableField("expire_date")
    private LocalDate expireDate;

    /** 提醒日期 */
    @TableField("remind_date")
    private LocalDate remindDate;

    /** 是否已发送提醒 0否 1是 */
    @TableField("sent")
    private Integer sent;
}
