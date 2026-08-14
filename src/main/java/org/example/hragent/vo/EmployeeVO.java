package org.example.hragent.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class EmployeeVO {
    private Long id;
    private String empNo;
    private String empName;
    private Integer gender;
    private LocalDate birthDate;
    private String phone;
    private String email;
    private String deptName;
    private String positionName;
    private LocalDate entryDate;
    private LocalDate regularDate;
    private LocalDate leaveDate;
    private Integer empStatus;
    private String workCity;
    private String remark;
}