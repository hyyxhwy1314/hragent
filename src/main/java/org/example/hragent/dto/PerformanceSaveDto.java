package org.example.hragent.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class PerformanceSaveDto {

    /**
     * 员工ID
     */
    @NotNull(message = "员工id不能为空")
    private Long empId;

    /**
     * 绩效周期编码
     */
    @NotBlank(message = "绩效周期不能为空")
    private String periodCode;

    /**
     * KPI指标数据JSON
     */
    private String kpiJson;

    /**
     * 自评分数
     */
    private BigDecimal selfScore;

    /**
     * 直属领导评分
     */
    private BigDecimal leaderScore;

    /**
     * 绩效最终得分
     */
    private BigDecimal finalScore;

    /**
     * 绩效等级，如S/A/B/C/D
     */
    private String performanceLevel;

    /**
     * AI生成评语
     */
    private String aiComment;

    /**
     * 关联流程实例ID
     */
    private Long flowInstanceId;

    /**
     * 绩效状态：0‑草稿、1‑待审核、2‑已完成、3‑驳回
     */
    private Integer status;
}