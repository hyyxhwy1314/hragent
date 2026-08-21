package org.example.hragent.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeAbilityRelUpdateDto {

    @NotNull(message = "id不能为空")
    private Long id;

    private Long resumeId;
    private Long abilityTagId;
    private BigDecimal confidence;
    private String source;
}
