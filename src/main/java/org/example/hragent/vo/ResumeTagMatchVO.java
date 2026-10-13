package org.example.hragent.vo;

import lombok.Data;

import java.util.List;

/**
 * 简历 AI 打分 - 单分类技能标签匹配明细
 */
@Data
public class ResumeTagMatchVO {
    /** 标签分类（如：技术技能） */
    private String category;
    /** 分类得分（0-100） */
    private Integer score;
    /** 命中技能标签 */
    private List<String> matched;
    /** 未命中技能标签 */
    private List<String> missing;
}