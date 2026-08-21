package org.example.hragent.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_performance")
public class Performance extends BaseEntity {

    /**
     * 所属员工id t_employee.id
     */
    @TableField("emp_id")
    private Long empId;

    /**
     * 绩效周期 2026Q3 / 2026-08
     */
    @TableField("period_code")
    private String periodCode;

    /**
     * KPI目标json
     */
    @TableField("kpi_json")
    private String kpiJson;

    /**
     * 员工自评分数
     */
    @TableField("self_score")
    private BigDecimal selfScore;

    /**
     * 主管评分
     */
    @TableField("leader_score")
    private BigDecimal leaderScore;

    /**
     * 最终得分
     */
    @TableField("final_score")
    private BigDecimal finalScore;

    /**
     * 绩效等级 S/A/B/C/D
     */
    @TableField("performance_level")
    private String performanceLevel;

    /**
     * AI生成评语
     */
    @TableField("ai_comment")
    private String aiComment;

    /**
     * 关联业务流程实例id t_flow_instance.id
     */
    @TableField("flow_instance_id")
    private Long flowInstanceId;

    /**
     * 记录状态 草稿/提交/审批完成
     */
    @TableField("status")
    private Integer status;
}
