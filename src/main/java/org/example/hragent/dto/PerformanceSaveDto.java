package org.example.hragent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PerformanceSaveDto {

    @NotNull(message = "员工id不能为空")
    private Long empId;

    @NotBlank(message = "绩效周期不能为空")
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