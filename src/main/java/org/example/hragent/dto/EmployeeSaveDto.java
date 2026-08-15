package org.example.hragent.dto;
import lombok.Data;

import java.time.LocalDate;

@Data
public class EmployeeSaveDto {

    /**
     * 员工编号
     */
    private String empNo;

    /**
     * 员工姓名
     */
    private String empName;

    /**
     * 性别：0‑未知，1‑男，2‑女
     */
    private Integer gender;

    /**
     * 出生日期
     */
    private LocalDate birthDate;

    /**
     * 手机号码
     */
    private String phone;

    /**
     * 邮箱地址
     */
    private String email;

    /**
     * 身份证号码
     */
    private String idCard;

    /**
     * 所属部门名称
     */
    private String deptName;

    /**
     * 职位名称
     */
    private String positionName;

    /**
     * 入职日期
     */
    private LocalDate entryDate;

    /**
     * 转正日期
     */
    private LocalDate regularDate;

    /**
     * 离职日期
     */
    private LocalDate leaveDate;

    /**
     * 员工状态：字典项，如0‑离职、1‑在职、2‑试用
     */
    private Integer empStatus;

    /**
     * 基本工资
     */
    private String baseSalary;

    /**
     * 工作城市
     */
    private String workCity;

    /**
     * 备注信息
     */
    private String remark;

    /**
     * 无参构造
     */
    public EmployeeSaveDto() {
    }
}