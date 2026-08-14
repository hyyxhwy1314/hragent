package org.example.hragent.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("t_employee")
public class Employee {
    /**
     * 员工工号
     */
    @TableField("emp_no")
    private String empNo;

    /**
     * 员工姓名
     */
    @TableField("emp_name")
    private String empName;

    /**
     * 性别 0女 1男
     */
    @TableField("gender")
    private Integer gender;

    /**
     * 出生日期
     */
    @TableField("birth_date")
    private LocalDate birthDate;

    /**
     * 手机号（密文）
     */
    @TableField("phone")
    private String phone;

    /**
     * 邮箱（密文）
     */
    @TableField("email")
    private String email;

    /**
     * 身份证号（密文）
     */
    @TableField("id_card")
    private String idCard;

    /**
     * 部门名称
     */
    @TableField("dept_name")
    private String deptName;

    /**
     * 岗位名称
     */
    @TableField("position_name")
    private String positionName;

    /**
     * 入职日期
     */
    @TableField("entry_date")
    private LocalDate entryDate;

    /**
     * 转正日期
     */
    @TableField("regular_date")
    private LocalDate regularDate;

    /**
     * 离职日期
     */
    @TableField("leave_date")
    private LocalDate leaveDate;

    /**
     * 员工状态 0在职 1离职
     */
    @TableField("emp_status")
    private Integer empStatus;

    /**
     * 基本工资（密文）
     */
    @TableField("base_salary")
    private String baseSalary;

    /**
     * 所在城市
     */
    @TableField("work_city")
    private String workCity;

    /**
     * 备注
     */
    @TableField("remark")
    private String remark;
}
