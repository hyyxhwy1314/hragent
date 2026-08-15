package org.example.hragent.vo;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 招聘岗位返回视图对象
 */
@Data
public class JobPostVO {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 岗位编码
     */
    private String jobCode;

    /**
     * 岗位名称
     */
    private String jobName;

    /**
     * 所属部门名称
     */
    private String deptName;

    /**
     * 工作城市
     */
    private String workCity;

    /**
     * 工作地点
     */
    private String workAddress;

    /**
     * 岗位职责
     */
    private String jobDuty;

    /**
     * 岗位要求
     */
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
     * 学历要求
     */
    private Integer educationReq;

    /**
     * 工作年限要求
     */
    private Integer workYearReq;

    /**
     * 招聘人数
     */
    private Integer headCount;

    /**
     * 岗位状态
     */
    private Integer jobStatus;

    /**
     * 是否对外公开：0‑否，1‑是
     */
    private Integer isPublic;

    /**
     * 发布时间
     */
    private LocalDateTime publishTime;

    /**
     * 截止时间
     */
    private LocalDateTime closeTime;

    /**
     * 创建人员工ID
     */
    private Long creatorEmpId;

    /**
     * 创建人员工姓名
     */
    private String creatorEmpName;
}