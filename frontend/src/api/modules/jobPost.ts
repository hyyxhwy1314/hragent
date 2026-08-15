﻿import { createCrudApi } from '../request'

export interface JobPost {
  id?: number
  jobCode?: string
  jobName?: string
  deptName?: string
  jobDesc?: string
  requirement?: string
  headcount?: number
  jobStatus?: number
  createTime?: string
  updateTime?: string
}

export const jobPostApi = createCrudApi<JobPost>('/job-posts')
