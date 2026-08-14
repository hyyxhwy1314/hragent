package org.example.hragent.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PerformanceVO {
    private Long id;
    private Long empId;
    private String empName;
    private String periodCode;
    private String kpiJson;
    private BigDecimal selfScore;
    private BigDecimal leaderScore;
    private BigDecimal finalScore;
    private String performanceLevel;
    private String aiComment;
    private Long flowInstanceId;
    private Integer status;
}