package org.example.hragent.vo;

import lombok.Data;

/**
 * 合同审查单条风险问题 VO
 */
@Data
public class ContractIssueVO {
    /** 涉及条款/章节位置 */
    private String contractClause;
    /** 风险类型：如 违约责任、薪资福利、保密协议、竞业限制、劳动关系终止 等 */
    private String issueType;
    /** 风险等级：高 / 中 / 低 */
    private String severity;
    /** 风险描述 */
    private String description;
    /** 修改建议 */
    private String suggestion;
}