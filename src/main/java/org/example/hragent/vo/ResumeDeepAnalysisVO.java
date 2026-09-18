package org.example.hragent.vo;

import lombok.Data;

import java.util.List;

/**
 * 简历 AI 深度分析结果 VO
 */
@Data
public class ResumeDeepAnalysisVO {
    /** 是否成功 */
    private Boolean success;
    /** 候选人整体评价 */
    private String summary;
    /** 核心优势 */
    private List<String> strengths;
    /** 潜在短板/风险 */
    private List<String> weaknesses;
    /** 岗位匹配分析 */
    private String matchAnalysis;
    /** 招聘/面试建议 */
    private List<String> suggestions;
}