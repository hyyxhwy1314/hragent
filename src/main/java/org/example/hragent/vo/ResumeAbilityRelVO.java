package org.example.hragent.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeAbilityRelVO {

    private Long id;
    private Long resumeId;
    private String resumeName;
    private Long abilityTagId;
    private String abilityTagName;
    private String abilityTagCode;
    private BigDecimal confidence;
    private String source;
}
