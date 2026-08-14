package org.example.hragent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class EmployeeUpdateDto {

    @NotNull(message = "id不能为空")
    private Long id;

    @NotBlank(message = "员工工号不能为空")
    private String empNo;

    @NotBlank(message = "员工姓名不能为空")
    private String empName;

    private Integer gender;

    private LocalDate birthDate;

    private String phone;

    private String email;

    private String idCard;

    @NotBlank(message = "部门名称不能为空")
    private String deptName;

    @NotBlank(message = "岗位名称不能为空")
    private String positionName;

    private LocalDate entryDate;

    private LocalDate regularDate;

    private LocalDate leaveDate;

    private Integer empStatus;

    private String baseSalary;

    private String workCity;

    private String remark;
}