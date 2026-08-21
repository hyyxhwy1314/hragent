package org.example.hragent.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public class ResumeQueryDto extends BaseQueryDto {

    private String resumeName;

    private Integer resumeStatus;

    private Long targetJobId;

    private Long ownerEmpId;

    private BigDecimal minMatchScore;
}
