package org.example.hragent.vo;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 简历‑能力标签关联返回视图对象
 */
@Data
public class ResumeAbilityRelVO {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 简历ID
     */
    private Long resumeId;

    /**
     * 能力标签ID
     */
    private Long abilityTagId;

    /**
     * 能力标签名称
     */
    private String tagName;

    /**
     * 置信度分数
     */
    private BigDecimal confidence;

    /**
     * 标签来源
     */
    private String source;
}