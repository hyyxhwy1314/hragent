import { httpGet, httpPost } from '../request'

/** 假期余额 */
export interface LeaveBalance {
  empId: number
  empName: string
  year: number
  totalDays: number
  usedDays: number
  remainingDays: number
}

/** 请假发起请求 */
export interface LeaveApplyReq {
  leaveType: string
  startDate: string
  endDate: string
  reason?: string
}

/** 请假发起结果 */
export interface LeaveApplyResult {
  requestId: number
  flowNo: string
  leaveDays: number
  balanceTip: string
}

/** 请假记录 */
export interface LeaveRequestRecord {
  id: number
  leaveType: string
  startDate: string
  endDate: string
  leaveDays: number
  reason?: string
  flowNo?: string
  flowStatus?: number
  statusText: string
  createTime?: string
}

/** 证明发起请求 */
export interface CertificateApplyReq {
  certType: string
  purpose?: string
}

/** 证明发起结果 */
export interface CertificateApplyResult {
  requestId: number
  flowNo: string
  certType: string
}

/** 证明申请记录 */
export interface CertificateRequestRecord {
  id: number
  certType: string
  purpose?: string
  content?: string
  flowNo?: string
  flowStatus?: number
  statusText: string
  createTime?: string
}

/** 到期提醒 */
export interface ExpireReminder {
  id: number
  remindType: string
  remindTypeText: string
  expireDate: string
  remindDate: string
  sent: number
}

/** 发起请假申请 */
export function applyLeave(data: LeaveApplyReq): Promise<LeaveApplyResult> {
  return httpPost('/self-service/leave/apply', data)
}

/** 查询当前员工假期余额 */
export function getLeaveBalance(): Promise<LeaveBalance> {
  return httpGet('/self-service/leave/balance')
}

/** 查询当前员工请假记录 */
export function getLeaveRecords(): Promise<LeaveRequestRecord[]> {
  return httpGet('/self-service/leave/records')
}

/** 发起证明申请 */
export function applyCertificate(data: CertificateApplyReq): Promise<CertificateApplyResult> {
  return httpPost('/self-service/certificate/apply', data)
}

/** 查询当前员工证明申请记录 */
export function getCertificateRecords(): Promise<CertificateRequestRecord[]> {
  return httpGet('/self-service/certificate/records')
}

/** 查询当前员工到期提醒 */
export function getReminders(): Promise<ExpireReminder[]> {
  return httpGet('/self-service/reminders')
}
