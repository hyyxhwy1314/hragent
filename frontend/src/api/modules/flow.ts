import { httpGet, httpPost, httpUpload, type PageVO, type PageParams } from '../request'

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
  applyEmpId: number
  /** 申请人姓名（后端回填） */
  applyEmpName?: string
  /** 业务对象名称：入职流程为简历名，其余为员工姓名（后端回填） */
  bizName?: string
}

/** 流程实例 */
export interface FlowInstance {
  id: number
  flowNo: string
  flowType: string
  bizId: number
  /** 业务对象类型：EMPLOYEE / RESUME / LEAVE_REQUEST */
  bizType?: string
  applyEmpId: number
  /** 申请人姓名（后端回填） */
  applyEmpName?: string
  /** 业务对象展示名（后端回填） */
  bizName?: string
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
  /** 业务主键ID（员工/简历/请假单ID）；不关联业务对象时不传 */
  bizId?: number
  /** 业务对象类型：EMPLOYEE / RESUME；内置流程可省略 */
  bizType?: string
  bizJson?: string
}

/** 审批请求 */
export interface TaskCompleteDTO {
  approved: boolean
  comment?: string
  delegateToEmpId?: number
  /** 抄送人员工ID列表（本节点审批完成时抄送） */
  ccEmpIds?: number[]
}

/** 抄送我的流程 */
export interface FlowCcVO {
  id: number
  flowInstanceId: number
  flowNo: string
  flowType: string
  bizId: number
  applyEmpId: number
  /** 申请人姓名（后端回填） */
  applyEmpName?: string
  /** 业务对象展示名（后端回填） */
  bizName?: string
  flowStatus: number
  nodeName: string
  readFlag: boolean
  createTime: string
}

/** 流程运行态图（BPMN XML + 节点高亮） */
export interface FlowDiagramVO {
  bpmnXml: string
  activeActivityIds: string[]
  finishedActivityIds: string[]
}

/** 已部署流程定义 */
export interface ProcessDefinition {
  id: string
  key: string
  name: string
  version: number
  deploymentId: string
}

/** 流程定义 XML 详情 */
export interface ProcessDefinitionXml {
  key: string
  name: string
  version: number
  bpmnXml: string
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
  LEAVE: '离职',
  LEAVE_REQUEST: '请假',
  CERTIFICATE: '证明',
  MULTI_SIGN: '会签/或签'
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

/** 流程实例列表（服务端分页） */
export function listInstances(params?: PageParams & {
  flowType?: string
  flowStatus?: number
  applyEmpId?: number
}): Promise<PageVO<FlowInstance>> {
  return httpGet('/flow/query/instances', { params })
}

/** 流程审批轨迹 */
export function getFlowTrace(flowInstanceId: number): Promise<FlowTraceVO[]> {
  return httpGet(`/flow/query/instances/${flowInstanceId}/trace`)
}

/** 流程运行态图（BPMN XML + 活动/已完成节点高亮） */
export function getFlowDiagram(flowInstanceId: number): Promise<FlowDiagramVO> {
  return httpGet(`/flow/query/instances/${flowInstanceId}/diagram`)
}

/** 抄送我的流程列表 */
export function listMyCc(): Promise<FlowCcVO[]> {
  return httpGet('/flow/query/cc/my')
}

/** 标记抄送记录已读 */
export function markCcRead(ccId: number): Promise<boolean> {
  return httpPost(`/flow/query/cc/${ccId}/read`)
}

/** 已部署流程定义列表 */
export function listDefinitions(): Promise<ProcessDefinition[]> {
  return httpGet('/flow/process/definitions')
}

/** 查询流程定义（最新版本）的 BPMN XML */
export function getDefinitionXml(processKey: string): Promise<ProcessDefinitionXml> {
  return httpGet(`/flow/process/definitions/${processKey}/xml`)
}

/** 部署 BPMN 流程定义（file 需携带 .bpmn20.xml 文件名后缀） */
export function deployProcess(file: File): Promise<Record<string, any>> {
  return httpUpload('/flow/process/deploy', file)
}
