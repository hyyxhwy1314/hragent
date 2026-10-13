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

export interface AiDailyStat {
  dateKey: string
  totalCount: number
  toolUsedCount: number
  totalInputTokens: number
  totalOutputTokens: number
  avgDurationMs: number
}

export interface DistributionItem {
  name: string
  value: number
}

export interface BehaviorModuleItem extends DistributionItem {
  tokenCount: number
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
export function getAiDaily(days?: number): Promise<AiDailyStat[]> {
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
  module?: string
  action: string
  summary: string
  detail: string
  status: string
  /** 操作人姓名 */
  username?: string
  /** AI 交互消耗的 Token 总数 */
  tokenCount?: number
  /** 流程申请人姓名（兼容字段） */
  applicant?: string
}

/** 获取最近活动日志 */
export function getRecentActivity(limit?: number): Promise<ActivityItem[]> {
  return httpGet('/dashboard/system/recent-activity', { params: { limit } })
}

export interface TokenSummary {
  todayTotal: number
  todayInput: number
  todayOutput: number
  periodTotal: number
  periodInput: number
  periodOutput: number
  avgPerDay: number
  peakDay: string
  peakValue: number
}

export interface TokenTrendItem {
  dateKey: string
  inputTokens: number
  outputTokens: number
}

export interface BehaviorLogItem {
  id: number
  userId: number
  username: string
  module: string
  action: string
  summary: string
  detail: string
  httpMethod: string
  requestUri: string
  status: string
  durationMs: number
  tokenCount: number
  createTime: string
}

export interface BehaviorLogPage {
  list: BehaviorLogItem[]
  total: number
  page: number
  size: number
}

/** Token 消耗看板汇总 */
export function getTokenSummary(days?: number): Promise<TokenSummary> {
  return httpGet('/dashboard/system/token-summary', { params: { days } })
}

/** Token 按天趋势 */
export function getTokenTrend(days?: number): Promise<TokenTrendItem[]> {
  return httpGet('/dashboard/system/token-trend', { params: { days } })
}

/** 行为日志按模块统计 */
export function getBehaviorModule(days?: number): Promise<BehaviorModuleItem[]> {
  return httpGet('/dashboard/system/behavior-module', { params: { days } })
}

/** 行为日志按天统计 */
export function getBehaviorDaily(days?: number): Promise<DistributionItem[]> {
  return httpGet('/dashboard/system/behavior-daily', { params: { days } })
}

/** 操作日志分页查询 */
export function getBehaviorLogs(params: {
  module?: string
  keyword?: string
  status?: string
  page?: number
  size?: number
}): Promise<BehaviorLogPage> {
  return httpGet('/dashboard/system/behavior-logs', { params })
}
