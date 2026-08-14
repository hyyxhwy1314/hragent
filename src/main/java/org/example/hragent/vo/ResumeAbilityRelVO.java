package org.example.hragent.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ResumeAbilityRelVO {
    private Long id;
    private Long resumeId;
    private Long abilityTagId;
    private String tagName;
    private BigDecimal confidence;
    private String source;
}