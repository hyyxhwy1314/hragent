package org.example.hragent.entity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_job_post")
public class JobPost extends BaseEntity {

    /**
     * 岗位编码
     */
    @TableField("job_code")
    private String jobCode;

    /**
     * 岗位名称
     */
    @TableField("job_name")
    private String jobName;

    /**
     * 所属部门
     */
    @TableField("dept_name")
    private String deptName;

    /**
     * 工作城市
     */
    @TableField("work_city")
    private String workCity;

    /**
     * 工作地址
     */
    @TableField("work_address")
    private String workAddress;

    /**
     * 岗位职责
     */
    @TableField("job_duty")
    private String jobDuty;

    /**
     * 任职要求
     */
    @TableField("job_requirement")
    private String jobRequirement;

    /**
     * 薪资下限
     */
    @TableField("salary_min")
    private BigDecimal salaryMin;

    /**
     * 薪资上限
     */
    @TableField("salary_max")
    private BigDecimal salaryMax;

    /**
     * 学历要求
     */
    @TableField("education_req")
    private Integer educationReq;

    /**
     * 工作年限要求
     */
    @TableField("work_year_req")
    private Integer workYearReq;

    /**
     * 招聘人数
     */
    @TableField("head_count")
    private Integer headCount;

    /**
     * 岗位状态 0关闭 1开放
     */
    @TableField("job_status")
    private Integer jobStatus;

    /**
     * 是否对外发布 0否 1是
     */
    @TableField("is_public")
    private Integer isPublic;

    /**
     * 发布时间
     */
    @TableField("publish_time")
    @JsonFormat(pattern = "yyyy‑MM‑dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime publishTime;

    /**
     * 截止时间
     */
    @TableField("close_time")
    @JsonFormat(pattern = "yyyy‑MM‑dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime closeTime;

    /**
     * 创建该岗位的HR，关联t_employee.id
     */
    @TableField("creator_emp_id")
    private Long creatorEmpId;

    /**
     * ES文档id
     */
    @TableField("jd_es_doc_id")
    private String jdEsDocId;
}