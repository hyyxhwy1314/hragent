package org.example.hragent.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobPostUpdateDto {

    /**
     * 岗位主键编号
     */
    @NotNull
    private Long id;

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

    /**
     * 岗位状态：0-草稿 1-招聘中 2-已关闭 3-已完成
     */
    private Integer jobStatus;

    private Integer isPublic;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime publishTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime closeTime;

    private Long creatorEmpId;
}
