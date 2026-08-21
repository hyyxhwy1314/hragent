import { createCrudApi, httpGet } from '../request'

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

/** 公开岗位VO（仅对外可见字段） */
export interface PublicJobPost {
  id: number
  jobName: string
  jobDuty?: string
  jobRequirement?: string
  workCity?: string
  salaryMin?: number | string
  salaryMax?: number | string
  educationReq?: number
  workYearReq?: number
  headCount?: number
  publishTime?: string
  closeTime?: string
}

export const jobPostApi = {
  ...createCrudApi<JobPost>('/job-posts'),
  /** 公开岗位列表（仅对外开放岗位） */
  publicList(): Promise<PublicJobPost[]> {
    return httpGet('/public/job-posts')
  },
  /** 公开岗位详情 */
  publicGetById(id: number): Promise<PublicJobPost> {
    return httpGet(`/public/job-posts/${id}`)
  }
}
