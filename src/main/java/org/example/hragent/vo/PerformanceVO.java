package org.example.hragent.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
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
