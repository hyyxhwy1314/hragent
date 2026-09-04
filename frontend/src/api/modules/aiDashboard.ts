import { httpGet } from '../request'

export interface DashboardSummary {
  totalCount: number
  totalToolCalls: number
  totalTokens: number
  avgDurationMs: number
  errorCount: number
}

export interface DailyStat {
  dateKey: string
  totalCount: number
  toolUsedCount: number
  totalInputTokens: number
  totalOutputTokens: number
  avgDurationMs: number
}

export interface ToolStat {
  name: string
  value: number
}

export function getSummary(days?: number): Promise<DashboardSummary> {
  return httpGet('/agent/dashboard/summary', { params: { days } })
}

export function getDaily(days?: number): Promise<DailyStat[]> {
  return httpGet('/agent/dashboard/daily', { params: { days } })
}

export function getToolDistribution(days?: number): Promise<ToolStat[]> {
  return httpGet('/agent/dashboard/tool-distribution', { params: { days } })
}