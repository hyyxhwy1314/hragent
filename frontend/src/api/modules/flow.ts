import { httpGet, httpPost } from '../request'

/** 流程任务 */
export interface TaskVO {
  taskId: string
  processInstanceId: string
  flowInstanceId: number
  taskName: string
  assigneeEmpId: number
  assigneeName: string
  flowType: string
  createTime: string
  bizId: number
}

/** 流程实例 */
export interface FlowInstance {
  id: number
  flowNo: string
  flowType: string
  bizId: number
  applyEmpId: number
  flowStatus: number
  flowableProcInstId: string
  bizJson: string
  createTime?: string
  updateTime?: string
}

/** 流程轨迹节点 */
export interface FlowTraceVO {
  nodeName: string
  assigneeName: string
  status: string
  approved?: boolean
  comment: string
  startTime: string
  endTime: string
}

/** 发起流程请求 */
export interface FlowStartDTO {
  processKey: string
  bizId: number
  bizJson?: string
}

/** 审批请求 */
export interface TaskCompleteDTO {
  approved: boolean
  comment?: string
  delegateToEmpId?: number
}

// 流程状态映射
export const FLOW_STATUS_MAP: Record<number, { label: string; color: string }> = {
  1: { label: '进行中', color: 'blue' },
  2: { label: '已通过', color: 'green' },
  3: { label: '已拒绝', color: 'red' },
  4: { label: '已撤回', color: 'default' }
}

// 流程类型映射
export const FLOW_TYPE_MAP: Record<string, string> = {
  ONBOARD: '入职',
  REGULAR: '转正',
  TRANSFER: '调岗',
  LEAVE: '离职'
}

/** 查待办 */
export function listTodoTasks(): Promise<TaskVO[]> {
  return httpGet('/flow/tasks/todo')
}

/** 查已办 */
export function listDoneTasks(): Promise<TaskVO[]> {
  return httpGet('/flow/tasks/done')
}

/** 完成任务（通过/拒绝） */
export function completeTask(taskId: string, data: TaskCompleteDTO): Promise<boolean> {
  return httpPost(`/flow/tasks/${taskId}/complete`, data)
}

/** 发起流程 */
export function startProcess(data: FlowStartDTO): Promise<FlowInstance> {
  return httpPost('/flow/process/start', data)
}

/** 撤回流程 */
export function cancelFlow(flowInstanceId: number): Promise<boolean> {
  return httpPost(`/flow/process/instances/${flowInstanceId}/cancel`)
}

/** 流程实例列表 */
export function listInstances(params?: {
  flowType?: string
  flowStatus?: number
  applyEmpId?: number
}): Promise<FlowInstance[]> {
  return httpGet('/flow/query/instances', { params })
}

/** 流程审批轨迹 */
export function getFlowTrace(flowInstanceId: number): Promise<FlowTraceVO[]> {
  return httpGet(`/flow/query/instances/${flowInstanceId}/trace`)
}
