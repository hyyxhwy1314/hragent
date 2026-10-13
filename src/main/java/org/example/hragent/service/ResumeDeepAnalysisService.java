package org.example.hragent.service;

import org.example.hragent.vo.ResumeDeepAnalysisVO;

/**
 * 简历 AI 深度分析服务：基于简历原文，由 AI 生成结构化深度评估
 */
public interface ResumeDeepAnalysisService {

    /**
     * 对指定简历做 AI 深度分析
     *
     * @param resumeId 简历ID
     * @return 结构化深度分析结果
     */
    ResumeDeepAnalysisVO analyzeDeep(Long resumeId);

    /**
     * 查询简历最近一次深度分析/评分结果
     *
     * @param resumeId 简历ID
     * @return 最近一次结果；无记录时 success=false
     */
    ResumeDeepAnalysisVO getScore(Long resumeId);
}