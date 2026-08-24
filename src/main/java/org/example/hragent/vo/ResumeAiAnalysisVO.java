package org.example.hragent.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 简历AI分析结果VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeAiAnalysisVO {
    /** 是否成功 */
    private Boolean success;
    /** 文件名 */
    private String filename;
    /** 简历文本 */
    private String resumeText;
    /** AI评价 */
    private String evaluation;
}