package org.example.hragent.dto;

import lombok.Data;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

@Data
public class JobPostSaveDto {
    @NotNull
    private String jobCode;

    @NotNull
    private String jobName;

    @NotNull
    private String deptName;

    @NotNull
    private String workCity;

    private String workAddress;

    @NotNull
    private String jobDuty;

    @NotNull
    private String jobRequirement;

    private BigDecimal salaryMin;

    private BigDecimal salaryMax;

    private Integer educationReq;

    private Integer workYearReq;

    @NotNull
    private Integer headCount;

    private Integer jobStatus;

    private Integer isPublic;

    private LocalDateTime publishTime;

    private LocalDateTime closeTime;

    @NotNull
    private Long creatorEmpId;
}
