package org.example.hragent.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ResumeVO {
    private Long id;
    private String resumeName;
    private Integer gender;
    private LocalDate birthDate;
    private String phone;
    private String email;
    private String expectPosition;
    private BigDecimal expectSalaryMin;
    private BigDecimal expectSalaryMax;
    private String expectCity;
    private Integer workYears;
    private Integer education;
    private String school;
    private String major;
    private String resumeContent;
    private Long resumeFileId;
    private Integer resumeStatus;
    private Integer deliverySource;
    private Long targetJobId;
    private String targetJobName;
    private BigDecimal matchScore;
    private String screeningOpinion;
    private Long ownerEmpId;
    private String ownerEmpName;
    private String remark;
}