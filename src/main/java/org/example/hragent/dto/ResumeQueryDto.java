package org.example.hragent.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ResumeQueryDto {
    private String resumeName;
    private Integer resumeStatus;
    private Long targetJobId;
    private Long ownerEmpId;
    private BigDecimal minMatchScore;
    private Long pageNum;
    private Long pageSize;
}
