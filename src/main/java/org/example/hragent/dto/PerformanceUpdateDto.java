package org.example.hragent.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PerformanceUpdateDto {
    @NotNull(message = "id不能为空")
    private Long id;
    private Long empId;
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