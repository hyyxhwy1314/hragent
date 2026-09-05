import { httpGet } from '../request'

export interface SystemSummary {
  aiTotalCount: number
  employeeCount: number
  resumeCount: number
  flowCount: number
  jobPostCount: number
  performanceCount: number
  trainingCount: number
}

export interface DailyBusinessStat {
  dateKey: string
  flowCount: number
  resumeCount: number
  performanceCount: number
}

export interface DistributionItem {
  name: string
  value: number
}

export interface FunnelItem {
  status_key: number
  value: number
}

/** 获取系统汇总数据（近 N 天） */
export function getSystemSummary(days?: number): Promise<SystemSummary> {
  return httpGet('/dashboard/system/summary', { params: { days } })
}

/** 获取 AI 按天趋势 */
export function getAiDaily(days?: number): Promise<DailyBusinessStat[]> {
  return httpGet('/dashboard/system/ai-daily', { params: { days } })
}

/** 获取业务操作按天趋势 */
export function getBusinessDaily(days?: number): Promise<DailyBusinessStat[]> {
  return httpGet('/dashboard/system/business-daily', { params: { days } })
}

/** 获取工具调用分布 */
export function getToolDistribution(days?: number): Promise<DistributionItem[]> {
  return httpGet('/dashboard/system/tool-distribution', { params: { days } })
}

/** 获取流程类型分布 */
export function getFlowDistribution(days?: number): Promise<DistributionItem[]> {
  return httpGet('/dashboard/system/flow-distribution', { params: { days } })
}

/** 获取招聘漏斗 */
export function getRecruitmentFunnel(): Promise<FunnelItem[]> {
  return httpGet('/dashboard/system/recruitment-funnel')
}

export interface ActivityItem {
  time: string
  type: string
  action: string
  summary: string
  detail: string
  status: string
  /** AI 交互消耗的 Token 总数 */
  tokenCount?: number
  /** 流程申请人姓名 */
  applicant?: string
}

/** 获取最近活动日志 */
export function getRecentActivity(limit?: number): Promise<ActivityItem[]> {
  return httpGet('/dashboard/system/recent-activity', { params: { limit } })
}
