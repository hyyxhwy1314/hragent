package org.example.hragent.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeAbilityRelSaveDto {

    @NotNull(message = "简历id不能为空")
    private Long resumeId;

    @NotNull(message = "能力标签id不能为空")
    private Long abilityTagId;

    /**
     * AI置信度 0-1
     */
    private BigDecimal confidence;

    /**
     * 来源：AI抽取 / HR手动标记
     */
    private String source;
}
