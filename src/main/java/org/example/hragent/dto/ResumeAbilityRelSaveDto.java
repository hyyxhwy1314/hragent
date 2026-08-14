package org.example.hragent.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ResumeAbilityRelSaveDto {

    @NotNull(message = "简历id不能为空")
    private Long resumeId;

    @NotNull(message = "标签id不能为空")
    private Long abilityTagId;

    private BigDecimal confidence;

    private String source;
}