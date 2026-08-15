package org.example.hragent.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ResumeAbilityRelQueryDto extends BaseQueryDto {

    private Long resumeId;

    private Long abilityTagId;

    private String source;
}
