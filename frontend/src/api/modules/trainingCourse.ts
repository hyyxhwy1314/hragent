import { createCrudApi } from '../request'

export interface TrainingCourse {
  id?: number
  courseName?: string
  courseCode?: string
  courseType?: string
  courseDesc?: string
  courseTarget?: string
  durationMin?: number
  tagIds?: string
  status?: number
  createTime?: string
  updateTime?: string
}

export const trainingCourseApi = createCrudApi<TrainingCourse>('/training-courses')
