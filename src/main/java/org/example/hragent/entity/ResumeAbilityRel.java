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
@TableName("t_resume_ability_rel")
public class ResumeAbilityRel extends BaseEntity {

    /**
     * 简历id t_resume.id
     */
    @TableField("resume_id")
    private Long resumeId;

    /**
     * 能力标签id t_ability_tag.id
     */
    @TableField("ability_tag_id")
    private Long abilityTagId;

    /**
     * AI置信度
     */
    @TableField("confidence")
    private BigDecimal confidence;

    /**
     * 来源：AI抽取 / HR手动标记
     */
    @TableField("source")
    private String source;
}
