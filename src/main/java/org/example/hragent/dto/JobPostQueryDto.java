package org.example.hragent.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class JobPostQueryDto extends BaseQueryDto {

    private String jobCode;

    private String jobName;

    private String deptName;

    private Integer jobStatus;
}
