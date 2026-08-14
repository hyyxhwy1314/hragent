package org.example.hragent.dto;

import lombok.Data;

@Data
public class JobPostQueryDto {
    private String jobCode;
    private String jobName;
    private String deptName;
    private Integer jobStatus;
    private Long pageNum;
    private Long pageSize;
}
