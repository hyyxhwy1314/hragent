package org.example.hragent.dto;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ResumeSaveDto {

    /**
     * 候选人姓名
     */
    @NotNull
    private String resumeName;

    /**
     * 性别：0‑未知，1‑男，2‑女
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
     * 身份证号码
     */
    private String idCard;

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
     * 学历：字典项
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
     * 简历原始文本内容
     */
    private String resumeContent;

    /**
     * 简历结构化解析JSON数据
     */
    private String resumeStructJson;

    /**
     * 简历附件文件ID
     */
    private Long resumeFileId;

    /**
     * 简历状态：0‑新建、1‑初筛通过、2‑面试中、3‑录用、4‑淘汰
     */
    private Integer resumeStatus;

    /**
     * 投递来源：招聘网站、内推、线下等字典项
     */
    private Integer deliverySource;

    /**
     * 应聘目标岗位ID
     */
    @NotNull
    private Long targetJobId;

    /**
     * 简历‑岗位AI匹配分数
     */
    private BigDecimal matchScore;

    /**
     * 初筛意见
     */
    private String screeningOpinion;

    /**
     * 负责人员工ID
     */
    private Long ownerEmpId;

    /**
     * 备注信息
     */
    private String remark;
}