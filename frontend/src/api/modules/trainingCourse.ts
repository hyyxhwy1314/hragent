﻿import { createCrudApi } from '../request'

export interface TrainingCourse {
  id?: number
  courseName?: string
  courseType?: string
  trainer?: string
  startDate?: string
  endDate?: string
  duration?: number
  status?: number
  description?: string
  createTime?: string
  updateTime?: string
}

export const trainingCourseApi = createCrudApi<TrainingCourse>('/training-courses')
