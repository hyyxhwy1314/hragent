import { httpPost } from '../request'

/** 合同审查单个风险问题 */
export interface ContractIssue {
  contractClause?: string
  issueType?: string
  severity?: string // 高 / 中 / 低
  description?: string
  suggestion?: string
}

/** 合同审查结果 */
export interface ContractReviewResult {
  success: boolean
  riskLevel?: string // 高 / 中 / 低
  summary?: string
  totalIssues?: number
  issues?: ContractIssue[]
}

export const contractReviewApi = {
  /** 审查合同文本，返回结构化风险报告 */
  review(content: string, contractName?: string): Promise<ContractReviewResult> {
    return httpPost<ContractReviewResult>('/contract-review', { content, contractName })
  }
}