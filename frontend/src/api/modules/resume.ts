import { createCrudApi } from '../request'

export interface Resume {
  id?: number
  resumeName?: string
  gender?: number
  birthDate?: string
  phone?: string
  email?: string
  idCard?: string
  expectPosition?: string
  expectSalaryMin?: number | string
  expectSalaryMax?: number | string
  expectCity?: string
  workYears?: number
  education?: number
  school?: string
  major?: string
  resumeContent?: string
  resumeStructJson?: string
  resumeFileId?: number
  resumeStatus?: number
  deliverySource?: number
  targetJobId?: number
  matchScore?: number | string
  screeningOpinion?: string
  ownerEmpId?: number
  remark?: string
  createTime?: string
  updateTime?: string
}

export const resumeApi = createCrudApi<Resume>('/resumes')
