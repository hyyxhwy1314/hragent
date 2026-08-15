package org.example.hragent.dto;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class JobPostSaveDto {

    /**
     * 岗位编码
     */
    @NotNull
    private String jobCode;

    /**
     * 岗位名称
     */
    @NotNull
    private String jobName;

    /**
     * 所属部门名称
     */
    @NotNull
    private String deptName;

    /**
     * 工作城市
     */
    @NotNull
    private String workCity;

    /**
     * 详细工作地址
     */
    private String workAddress;

    /**
     * 岗位职责描述
     */
    @NotNull
    private String jobDuty;

    /**
     * 岗位任职要求
     */
    @NotNull
    private String jobRequirement;

    /**
     * 薪资下限
     */
    private BigDecimal salaryMin;

    /**
     * 薪资上限
     */
    private BigDecimal salaryMax;

    /**
     * 学历要求：字典项
     */
    private Integer educationReq;

    /**
     * 工作年限要求：字典项
     */
    private Integer workYearReq;

    /**
     * 招聘人数
     */
    @NotNull
    private Integer headCount;

    /**
     * 岗位状态：0‑草稿 1‑招聘中 2‑已关闭 3‑已完成
     */
    private Integer jobStatus;

    /**
     * 是否对外公开：0‑不公开 1‑公开
     */
    private Integer isPublic;

    /**
     * 岗位发布时间
     */
    @JsonFormat(pattern = "yyyy‑MM‑dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime publishTime;

    /**
     * 招聘截止关闭时间
     */
    @JsonFormat(pattern = "yyyy‑MM‑dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime closeTime;

    /**
     * 创建人员工ID
     */
    @NotNull
    private Long creatorEmpId;
}