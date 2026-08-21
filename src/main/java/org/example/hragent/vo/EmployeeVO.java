package org.example.hragent.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeVO {
    private Long id;
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
    private String role;
    private Long leaderId;
    private String remark;
    private String createTime;
    private String updateTime;
}
