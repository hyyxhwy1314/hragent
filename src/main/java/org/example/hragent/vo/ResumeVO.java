package org.example.hragent.vo;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 简历返回视图对象
 */
@Data
public class ResumeVO {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 简历姓名
     */
    private String resumeName;

    /**
     * 性别
     */
    private Integer gender;

    /**
     * 出生日期
     */
    private LocalDate birthDate;

    /**
     * 联系电话
     */
    private String phone;

    /**
     * 电子邮箱
     */
    private String email;

    /**
     * 期望岗位
     */
    private String expectPosition;

    /**
     * 期望薪资下限
     */
    private BigDecimal expectSalaryMin;

    /**
     * 期望薪资上限
     */
    private BigDecimal expectSalaryMax;

    /**
     * 期望工作城市
     */
    private String expectCity;

    /**
     * 工作年限
     */
    private Integer workYears;

    /**
     * 学历
     */
    private Integer education;

    /**
     * 毕业院校
     */
    private String school;

    /**
     * 所学专业
     */
    private String major;

    /**
     * 简历正文内容
     */
    private String resumeContent;

    /**
     * 简历附件文件ID
     */
    private Long resumeFileId;

    /**
     * 简历状态
     */
    private Integer resumeStatus;

    /**
     * 简历投递来源
     */
    private Integer deliverySource;

    /**
     * 目标岗位ID
     */
    private Long targetJobId;

    /**
     * 目标岗位名称
     */
    private String targetJobName;

    /**
     * 岗位匹配分数
     */
    private BigDecimal matchScore;

    /**
     * 筛选评语
     */
    private String screeningOpinion;

    /**
     * 负责人员工ID
     */
    private Long ownerEmpId;

    /**
     * 负责人员工姓名
     */
    private String ownerEmpName;

    /**
     * 备注
     */
    private String remark;
}