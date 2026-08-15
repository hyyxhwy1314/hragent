﻿import { createCrudApi } from '../request'

export interface Resume {
  id?: number
  resumeName?: string
  targetJobId?: number
  ownerEmpId?: number
  matchScore?: number
  resumeStatus?: number
  rawContent?: string
  parseResult?: string
  createTime?: string
  updateTime?: string
}

export const resumeApi = createCrudApi<Resume>('/resumes')
