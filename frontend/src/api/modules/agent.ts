import { httpGet, httpPost, httpDelete } from '../request'

export interface AgentChatRequest {
  message: string
  userId?: string
}

export interface ToolCallStep {
  toolName: string
  arguments: string
  result: string | null
  timestamp: string
}

export interface AgentChatResponse {
  response: string
  sessionId: string
  /** 思考过程（工具调用步骤汇总） */
  thinking: string | null
  /** 工具调用步骤明细 */
  toolSteps: ToolCallStep[] | null
  /** 本回合消耗的输入 Token */
  inputTokens: number
  /** 本回合消耗的输出 Token */
  outputTokens: number
}

export interface AgentSession {
  id: number
  sessionId: string
  userId: number
  status: number
  intent: string
  title: string
  startTime: string
  endTime: string
  messageCount: number
  remark: string
  createTime: string
}

export interface AgentMessage {
  id: number
  sessionId: string
  role: string
  content: string
  messageType: string
  createTime: string
}

export function agentChat(data: AgentChatRequest): Promise<AgentChatResponse> {
  return httpPost('/agent/chat', data)
}

export function agentContinueChat(sessionId: string, data: AgentChatRequest): Promise<AgentChatResponse> {
  return httpPost(`/agent/chat/${sessionId}`, data)
}

export function agentClearSession(sessionId: string): Promise<boolean> {
  return httpDelete(`/agent/session/${sessionId}`)
}

export function getSessions(): Promise<AgentSession[]> {
  return httpGet('/agent/sessions')
}

export function getSessionMessages(sessionId: string): Promise<AgentMessage[]> {
  return httpGet(`/agent/session/${sessionId}/messages`)
}