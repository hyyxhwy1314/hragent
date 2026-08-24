package org.example.hragent.service;

import org.example.hragent.vo.ResumeAiAnalysisVO;

/**
 * 简历AI分析服务接口
 */
public interface ResumeAiAnalysisService {
    /**
     * 调用Python服务进行简历AI分析
     * @param fileBytes 简历文件字节数组
     * @param filename 简历文件名
     * @return AI分析结果
     */
    ResumeAiAnalysisVO analyzeResume(byte[] fileBytes, String filename);
}