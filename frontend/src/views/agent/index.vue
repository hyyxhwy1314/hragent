<script setup lang="ts">
import { ref, nextTick, onMounted, computed } from 'vue'
import { Input, Button, Spin, Tag, Tooltip, message } from 'ant-design-vue'
import { SendOutlined, ClearOutlined, RobotOutlined, UserOutlined, PlusOutlined, DeleteOutlined, HistoryOutlined } from '@ant-design/icons-vue'
import { agentChat, agentContinueChat, agentClearSession, getSessions, getSessionMessages } from '@/api/modules/agent'
import type { AgentSession, AgentMessage } from '@/api/modules/agent'

interface Message {
  role: 'user' | 'assistant'
  content: string
  timestamp: string
  loading?: boolean
}

const messages = ref<Message[]>([])
const inputText = ref('')
const sending = ref(false)
const sessionId = ref<string | null>(null)
const sessions = ref<AgentSession[]>([])
const showHistory = ref(true)
const messagesRef = ref<HTMLElement | null>(null)

// 加载会话列表
async function loadSessions() {
  try {
    sessions.value = await getSessions()
  } catch { /* ignore */ }
}

onMounted(() => {
  loadSessions()
  addWelcomeMessage()
})

function addWelcomeMessage() {
  messages.value.push({
    role: 'assistant',
    content: '您好！我是 HR 智能助手，可以帮您查询员工信息、岗位信息、简历信息，发起入职/离职/调岗流程，查询绩效结果、培训课程等。请问有什么可以帮您的？',
    timestamp: formatTime(new Date())
  })
}

function formatTime(date: Date): string {
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function formatDate(dt: string): string {
  const d = new Date(dt)
  const now = new Date()
  const isToday = d.toDateString() === now.toDateString()
  if (isToday) return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  return `${d.getMonth() + 1}/${d.getDate()} ${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}

function scrollToBottom() {
  nextTick(() => {
    const container = messagesRef.value
    if (container) container.scrollTop = container.scrollHeight
  })
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || sending.value) return

  inputText.value = ''
  sending.value = true

  messages.value.push({
    role: 'user',
    content: text,
    timestamp: formatTime(new Date())
  })
  scrollToBottom()

  const aiMsgIndex = messages.value.length
  messages.value.push({
    role: 'assistant',
    content: '',
    timestamp: formatTime(new Date()),
    loading: true
  })

  try {
    let result
    if (sessionId.value) {
      result = await agentContinueChat(sessionId.value, { message: text })
    } else {
      result = await agentChat({ message: text })
      sessionId.value = result.sessionId
    }

    messages.value[aiMsgIndex].content = result.response
    messages.value[aiMsgIndex].loading = false

    // 刷新会话列表
    loadSessions()
  } catch (e: any) {
    messages.value[aiMsgIndex].content = '抱歉，请求处理失败：' + (e?.message || '未知错误')
    messages.value[aiMsgIndex].loading = false
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

async function newConversation() {
  sessionId.value = null
  messages.value = []
  addWelcomeMessage()
}

async function selectSession(session: AgentSession) {
  if (sessionId.value === session.sessionId) return
  sessionId.value = session.sessionId
  messages.value = []
  sending.value = true

  try {
    const msgs = await getSessionMessages(session.sessionId)
    for (const msg of msgs) {
      if (msg.role === 'user' || msg.role === 'assistant') {
        messages.value.push({
          role: msg.role as 'user' | 'assistant',
          content: msg.content,
          timestamp: formatTime(new Date(msg.createTime))
        })
      }
    }
  } catch (e: any) {
    message.error('加载历史消息失败')
    addWelcomeMessage()
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

async function deleteSession(session: AgentSession) {
  try {
    await agentClearSession(session.sessionId)
    sessions.value = sessions.value.filter(s => s.sessionId !== session.sessionId)
    if (sessionId.value === session.sessionId) {
      newConversation()
    }
    message.success('会话已删除')
  } catch (e: any) {
    message.error('删除会话失败')
  }
}

async function clearConversation() {
  if (sessionId.value) {
    try {
      await agentClearSession(sessionId.value)
      sessions.value = sessions.value.filter(s => s.sessionId !== sessionId.value)
    } catch { /* ignore */ }
  }
  newConversation()
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

function getSessionTitle(session: AgentSession): string {
  return session.title || `会话 ${session.id}`
}

const currentSession = computed(() => {
  return sessions.value.find(s => s.sessionId === sessionId.value) || null
})
</script>

<template>
  <div class="chat-page">
    <div class="chat-layout">
      <!-- 历史会话侧边栏 -->
      <div class="history-sidebar" :class="{ collapsed: !showHistory }">
        <div class="sidebar-header">
          <HistoryOutlined class="sidebar-icon" />
          <span class="sidebar-title">历史对话</span>
        </div>

        <div class="sidebar-body">
          <div v-if="sessions.length === 0" class="sidebar-empty">
            <span>暂无历史记录</span>
          </div>
          <div v-else class="session-list">
            <div
              v-for="session in sessions"
              :key="session.sessionId"
              class="session-item"
              :class="{ active: session.sessionId === sessionId }"
              @click="selectSession(session)"
            >
              <div class="session-info">
                <div class="session-label">{{ getSessionTitle(session) }}</div>
                <div class="session-meta">
                  <span class="session-time">{{ formatDate(session.createTime) }}</span>
                </div>
              </div>
              <div class="session-actions">
                <DeleteOutlined class="session-delete" @click.stop="deleteSession(session)" />
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 主聊天区域 -->
      <div class="chat-main">
        <!-- 头部 -->
        <div class="chat-header">
          <div class="header-left">
            <RobotOutlined class="header-icon" />
            <span class="header-title">HR 智能助手</span>
            <Tag color="blue" class="header-tag">AI</Tag>
          </div>
          <div class="header-actions">
            <Tooltip title="新对话">
              <Button type="text" size="small" @click="newConversation">
                <PlusOutlined />
              </Button>
            </Tooltip>
            <Tooltip title="清除当前对话">
              <Button type="text" size="small" @click="clearConversation">
                <ClearOutlined />
              </Button>
            </Tooltip>
          </div>
        </div>

        <!-- 消息区域 -->
        <div class="chat-messages" ref="messagesRef">
          <template v-for="(msg, index) in messages" :key="index">
            <div v-if="msg.content || msg.loading" class="message-row" :class="msg.role">
              <div class="avatar" :class="msg.role">
                <UserOutlined v-if="msg.role === 'user'" />
                <RobotOutlined v-else />
              </div>
              <div class="bubble" :class="msg.role">
                <div class="bubble-content">
                  <Spin v-if="msg.loading" size="small" />
                  <div v-else class="markdown-text">{{ msg.content }}</div>
                </div>
                <div v-if="!msg.loading" class="bubble-time">{{ msg.timestamp }}</div>
              </div>
            </div>
          </template>
        </div>

        <!-- 输入区域 -->
        <div class="chat-input-area">
          <div class="input-wrapper">
            <Input.TextArea
              v-model:value="inputText"
              :rows="2"
              placeholder="输入您的问题，例如：查询员工张三的信息"
              :disabled="sending"
              @keydown="handleKeydown"
              class="chat-input"
            />
            <Button
              type="primary"
              shape="circle"
              size="large"
              :loading="sending"
              :disabled="!inputText.trim() || sending"
              @click="sendMessage"
              class="send-btn"
            >
              <SendOutlined />
            </Button>
          </div>
          <div class="input-hint">
            按 Enter 发送，Shift+Enter 换行
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
  overflow: hidden;
}

.chat-layout {
  display: flex;
  height: 100%;
  gap: 0;
  overflow: hidden;
}

/* 历史侧边栏 */
.history-sidebar {
  width: 240px;
  min-width: 240px;
  background: #fff;
  border-right: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sidebar-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}

.sidebar-icon {
  font-size: 16px;
  color: rgba(0, 0, 0, 0.45);
}

.sidebar-title {
  font-size: 14px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.85);
}

.sidebar-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.sidebar-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: rgba(0, 0, 0, 0.35);
  font-size: 13px;
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.session-item:hover {
  background: #f5f7fa;
}

.session-item.active {
  background: #e6f0ff;
}

.session-info {
  flex: 1;
  min-width: 0;
}

.session-label {
  font-size: 13px;
  color: rgba(0, 0, 0, 0.85);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-meta {
  display: flex;
  gap: 8px;
  margin-top: 2px;
  font-size: 11px;
  color: rgba(0, 0, 0, 0.35);
}

.session-delete {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.25);
  opacity: 0;
  transition: opacity 0.2s;
}

.session-item:hover .session-delete {
  opacity: 1;
}

.session-delete:hover {
  color: #ff4d4f;
}

/* 主聊天区域 */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
  overflow: hidden;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-actions {
  display: flex;
  gap: 4px;
}

.header-icon {
  font-size: 20px;
  color: #2F54EB;
}

.header-title {
  font-size: 15px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.85);
}

.header-tag {
  font-size: 11px;
  line-height: 18px;
}

/* 消息区域 */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  scroll-behavior: smooth;
}

.message-row {
  display: flex;
  gap: 10px;
  max-width: 85%;
}

.message-row.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.message-row.assistant {
  align-self: flex-start;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
}

.avatar.user {
  background: linear-gradient(135deg, #2F54EB, #597EF7);
  color: #fff;
}

.avatar.assistant {
  background: linear-gradient(135deg, #13C2C2, #36CFC9);
  color: #fff;
}

.bubble {
  padding: 10px 14px;
  border-radius: 12px;
  position: relative;
  max-width: 100%;
}

.bubble.user {
  background: #2F54EB;
  color: #fff;
  border-bottom-right-radius: 4px;
}

.bubble.assistant {
  background: #f0f2f5;
  color: rgba(0, 0, 0, 0.85);
  border-bottom-left-radius: 4px;
}

.bubble-content {
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.bubble-time {
  font-size: 11px;
  color: rgba(0, 0, 0, 0.35);
  margin-top: 4px;
  text-align: right;
}

.bubble.user .bubble-time {
  color: rgba(255, 255, 255, 0.6);
}

/* 输入区域 */
.chat-input-area {
  padding: 12px 24px 16px;
  border-top: 1px solid #f0f0f0;
  flex-shrink: 0;
  background: #fff;
}

.input-wrapper {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}

.chat-input {
  flex: 1;
}

.send-btn {
  flex-shrink: 0;
  margin-bottom: 2px;
}

.input-hint {
  font-size: 11px;
  color: rgba(0, 0, 0, 0.35);
  margin-top: 6px;
  text-align: right;
}
</style>