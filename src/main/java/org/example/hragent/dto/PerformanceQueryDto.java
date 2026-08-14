package org.example.hragent.dto;

import lombok.Data;

@Data
public class PerformanceQueryDto {
    private Long empId;
    private String periodCode;
    private Integer status;
    private Long pageNum;
    private Long pageSize;
}