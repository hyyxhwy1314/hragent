package org.example.hragent.dto;

import lombok.Data;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;

@Data
public class EmployeeSaveDto {

    private String empNo;


    private String empName;

    private Integer gender;

    private LocalDate birthDate;

    private String phone;

    private String email;

    private String idCard;


    private String deptName;


    private String positionName;


    private LocalDate entryDate;

    private LocalDate regularDate;

    private LocalDate leaveDate;

    private Integer empStatus;

    private String baseSalary;

    private String workCity;

    private String remark;
    public EmployeeSaveDto() {
    }
}
