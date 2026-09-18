package org.example.hragent.vo;

import lombok.Data;

import java.util.List;

/**
 * 合同审查结果 VO
 */
@Data
public class ContractReviewVO {
    /** 是否成功 */
    private Boolean success;
    /** 综合风险等级：高 / 中 / 低 */
    private String riskLevel;
    /** 总体评价 */
    private String summary;
    /** 风险问题数量 */
    private Integer totalIssues;
    /** 风险问题明细 */
    private List<ContractIssueVO> issues;
}