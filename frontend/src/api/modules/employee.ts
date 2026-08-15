﻿import { createCrudApi } from '../request'

export interface Employee {
  id?: number
  empNo?: string
  empName?: string
  gender?: number
  birthDate?: string
  phone?: string
  email?: string
  deptName?: string
  positionName?: string
  entryDate?: string
  regularDate?: string
  leaveDate?: string
  empStatus?: number
  workCity?: string
  remark?: string
  createTime?: string
  updateTime?: string
}

export const employeeApi = createCrudApi<Employee>('/employees')
