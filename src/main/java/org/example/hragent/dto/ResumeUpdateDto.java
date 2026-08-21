package org.example.hragent.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeUpdateDto {

    @NotNull(message = "id不能为空")
    private Long id;

    private String resumeName;
    private Integer gender;
    private LocalDate birthDate;
    private String phone;
    private String email;
    private String idCard;
    private String expectPosition;
    private BigDecimal expectSalaryMin;
    private BigDecimal expectSalaryMax;
    private String expectCity;
    private Integer workYears;
    private Integer education;
    private String school;
    private String major;
    private String resumeContent;
    private String resumeStructJson;
    private Long resumeFileId;
    private Integer resumeStatus;
    private Integer deliverySource;
    private Long targetJobId;
    private BigDecimal matchScore;
    private String screeningOpinion;
    private Long ownerEmpId;
    private String remark;
}
