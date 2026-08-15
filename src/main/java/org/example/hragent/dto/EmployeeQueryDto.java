package org.example.hragent.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class EmployeeQueryDto extends BaseQueryDto {

    private String empNo;

    private String empName;

    private String deptName;

    private Integer empStatus;
}
