package org.example.hragent.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("t_employee")
public class Employee extends BaseEntity {

    @TableField("emp_no")
    private String empNo;

    @TableField("emp_name")
    private String empName;

    /**
     * 登录密码（BCrypt 哈希），新增员工时默认 123456 的哈希
     */
    @TableField("password")
    private String password;

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

    /**
     * 角色：EMPLOYEE/DEPT_LEADER/HR/HRBP/ADMIN
     * 用于工作流审批人解析与接口权限校验
     */
    @TableField("role")
    private String role;

    /**
     * 直属上级ID（t_employee.id），用于审批人解析
     */
    @TableField("leader_id")
    private Long leaderId;

    @TableField("remark")
    private String remark;
}
