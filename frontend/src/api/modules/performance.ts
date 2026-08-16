import { createCrudApi } from '../request'

export interface Performance {
  id?: number
  empId?: number
  periodCode?: string
  kpiJson?: string
  selfScore?: number | string
  leaderScore?: number | string
  finalScore?: number | string
  performanceLevel?: string
  aiComment?: string
  flowInstanceId?: number
  status?: number
  createTime?: string
  updateTime?: string
}

export const performanceApi = createCrudApi<Performance>('/performances')
