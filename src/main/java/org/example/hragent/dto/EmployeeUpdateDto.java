package org.example.hragent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeUpdateDto {

    /**
     * 员工主键ID
     */
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
    private String remark;
}
