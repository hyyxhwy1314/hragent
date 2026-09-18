package org.example.hragent.service;

import org.example.hragent.vo.ContractReviewVO;

/**
 * 合同审查服务：基于 AI 大模型识别合同中的潜在法律风险并给出修改建议
 */
public interface ContractReviewService {

    /**
     * 审查合同文本，返回结构化风险报告
     *
     * @param content      合同全文
     * @param contractName 合同名称（可选）
     * @return 结构化风险报告
     */
    ContractReviewVO review(String content, String contractName);
}