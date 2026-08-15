package org.example.hragent.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ResumeAbilityRelUpdateDto {

    @NotNull(message = "id不能为空")
    private Long id;

    private Long resumeId;

    private Long abilityTagId;

    private BigDecimal confidence;

    private String source;
}
