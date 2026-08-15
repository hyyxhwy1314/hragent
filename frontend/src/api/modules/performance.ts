﻿import { createCrudApi } from '../request'

export interface Performance {
  id?: number
  empId?: number
  periodCode?: string
  kpiScore?: number
  attitudeScore?: number
  abilityScore?: number
  overallScore?: number
  rankLevel?: string
  status?: number
  comment?: string
  createTime?: string
  updateTime?: string
}

export const performanceApi = createCrudApi<Performance>('/performances')
