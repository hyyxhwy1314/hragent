package org.example.hragent.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("t_resume")
public class Resume extends BaseEntity {

    /**
     * 候选人姓名
     */
    @TableField("resume_name")
    private String resumeName;

    /**
     * 性别 0女 1男
     */
    @TableField("gender")
    private Integer gender;

    /**
     * 出生日期
     */
    @TableField("birth_date")
    private LocalDate birthDate;

    /**
     * 手机号（密文）
     */
    @TableField("phone")
    private String phone;

    /**
     * 邮箱（密文）
     */
    @TableField("email")
    private String email;

    /**
     * 身份证（密文）
     */
    @TableField("id_card")
    private String idCard;

    /**
     * 意向岗位
     */
    @TableField("expect_position")
    private String expectPosition;

    /**
     * 期望薪资下限
     */
    @TableField("expect_salary_min")
    private BigDecimal expectSalaryMin;

    /**
     * 期望薪资上限
     */
    @TableField("expect_salary_max")
    private BigDecimal expectSalaryMax;

    /**
     * 意向城市
     */
    @TableField("expect_city")
    private String expectCity;

    /**
     * 工作年限
     */
    @TableField("work_years")
    private Integer workYears;

    /**
     * 学历
     */
    @TableField("education")
    private Integer education;

    /**
     * 毕业学校
     */
    @TableField("school")
    private String school;

    /**
     * 专业
     */
    @TableField("major")
    private String major;

    /**
     * 简历原始文本
     */
    @TableField("resume_content")
    private String resumeContent;

    /**
     * AI解析后结构化json
     */
    @TableField("resume_struct_json")
    private String resumeStructJson;

    /**
     * 文件id
     */
    @TableField("resume_file_id")
    private Long resumeFileId;

    /**
     * 简历状态 待筛选/面试中/录用/归档
     */
    @TableField("resume_status")
    private Integer resumeStatus;

    /**
     * 简历来源
     */
    @TableField("delivery_source")
    private Integer deliverySource;

    /**
     * 投递岗位id t_job_post.id
     */
    @TableField("target_job_id")
    private Long targetJobId;

    /**
     * AI人岗匹配分数
     */
    @TableField("match_score")
    private BigDecimal matchScore;

    /**
     * AI筛选评语
     */
    @TableField("screening_opinion")
    private String screeningOpinion;

    /**
     * 负责HR t_employee.id
     */
    @TableField("owner_emp_id")
    private Long ownerEmpId;

    /**
     * 备注
     */
    @TableField("remark")
    private String remark;
}