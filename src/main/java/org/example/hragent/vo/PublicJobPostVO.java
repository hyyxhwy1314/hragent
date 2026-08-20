package org.example.hragent.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 公开岗位VO
 * 仅暴露对外可见的岗位名称与描述信息，不含内部数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicJobPostVO {

    private Long id;

    /** 岗位名称 */
    private String jobName;

    /** 岗位职责描述 */
    private String jobDuty;

    /** 任职要求 */
    private String jobRequirement;

    /** 工作城市 */
    private String workCity;

    /** 薪资下限 */
    private BigDecimal salaryMin;

    /** 薪资上限 */
    private BigDecimal salaryMax;

    /** 学历要求 */
    private Integer educationReq;

    /** 工作年限要求 */
    private Integer workYearReq;

    /** 招聘人数 */
    private Integer headCount;

    /** 发布时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime publishTime;

    /** 截止时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime closeTime;
}
