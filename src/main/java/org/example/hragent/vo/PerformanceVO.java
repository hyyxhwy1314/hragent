package org.example.hragent.vo;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 绩效记录返回视图对象
 */
@Data
public class PerformanceVO {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 员工ID
     */
    private Long empId;

    /**
     * 员工姓名
     */
    private String empName;

    /**
     * 考核周期编码
     */
    private String periodCode;

    /**
     * KPI指标JSON数据
     */
    private String kpiJson;

    /**
     * 自评分数
     */
    private BigDecimal selfScore;

    /**
     * 上级评分
     */
    private BigDecimal leaderScore;

    /**
     * 最终绩效得分
     */
    private BigDecimal finalScore;

    /**
     * 绩效等级
     */
    private String performanceLevel;

    /**
     * AI评语
     */
    private String aiComment;

    /**
     * 关联流程实例ID
     */
    private Long flowInstanceId;

    /**
     * 绩效单据状态
     */
    private Integer status;
}