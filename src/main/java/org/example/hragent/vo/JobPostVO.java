package org.example.hragent.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobPostVO {

    private Long id;
    private String jobCode;
    private String jobName;
    private String deptName;
    private String workCity;
    private String workAddress;
    private String jobDuty;
    private String jobRequirement;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private Integer educationReq;
    private Integer workYearReq;
    private Integer headCount;
    private Integer jobStatus;
    private Integer isPublic;
    private LocalDateTime publishTime;
    private LocalDateTime closeTime;
    private Long creatorEmpId;
    private String creatorEmpName;
}
