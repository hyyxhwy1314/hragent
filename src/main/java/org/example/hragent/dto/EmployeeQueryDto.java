package org.example.hragent.dto;

import lombok.Data;

@Data
public class EmployeeQueryDto {
    private String empNo;
    private String empName;
    private String deptName;
    private Integer empStatus;
    private Long pageNum;
    private Long pageSize;
}
