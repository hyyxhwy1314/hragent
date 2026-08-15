package org.example.hragent.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_employee")
public class Employee extends BaseEntity {

    @TableField("emp_no")
    private String empNo;

    @TableField("emp_name")
    private String empName;

    @TableField("gender")
    private Integer gender;

    @TableField("birth_date")
    private LocalDate birthDate;

    @TableField("phone")
    private String phone;

    @TableField("email")
    private String email;

    @TableField("id_card")
    private String idCard;

    @TableField("dept_name")
    private String deptName;

    @TableField("position_name")
    private String positionName;

    @TableField("entry_date")
    private LocalDate entryDate;

    @TableField("regular_date")
    private LocalDate regularDate;

    @TableField("leave_date")
    private LocalDate leaveDate;

    @TableField("emp_status")
    private Integer empStatus;

    @TableField("base_salary")
    private String baseSalary;

    @TableField("work_city")
    private String workCity;

    @TableField("remark")
    private String remark;
}
