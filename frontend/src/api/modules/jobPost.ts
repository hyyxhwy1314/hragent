import { createCrudApi } from '../request'

export interface JobPost {
  id?: number
  jobCode?: string
  jobName?: string
  deptName?: string
  workCity?: string
  workAddress?: string
  jobDuty?: string
  jobRequirement?: string
  salaryMin?: number | string
  salaryMax?: number | string
  educationReq?: number
  workYearReq?: number
  headCount?: number
  jobStatus?: number
  isPublic?: number
  publishTime?: string
  closeTime?: string
  creatorEmpId?: number
  createTime?: string
  updateTime?: string
}

export const jobPostApi = createCrudApi<JobPost>('/job-posts')
