package org.example.hragent.dto;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ResumeAbilityRelSaveDto {

    /**
     * 简历主键ID
     */
    @NotNull(message = "简历id不能为空")
    private Long resumeId;

    /**
     * 能力标签主键ID
     */
    @NotNull(message = "标签id不能为空")
    private Long abilityTagId;

    /**
     * 置信度（AI识别匹配分值）
     */
    private BigDecimal confidence;

    /**
     * 标签来源：人工标注 / AI自动识别
     */
    private String source;
}