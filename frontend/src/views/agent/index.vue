<script setup lang="ts">
import { ref, nextTick, onMounted, computed, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { Drawer, Input, Button, Spin, Tag, Tooltip, message } from 'ant-design-vue'
import {
  SendOutlined, ClearOutlined, RobotOutlined, UserOutlined,
  PlusOutlined, DeleteOutlined, HistoryOutlined, BulbOutlined,
  CaretRightOutlined, CaretDownOutlined, ToolOutlined, CopyOutlined,
  ApartmentOutlined, TeamOutlined, TrophyOutlined, BookOutlined
} from '@ant-design/icons-vue'
import { agentChatAsync, getAgentTask, agentClearSession, getSessions, getSessionMessages } from '@/api/modules/agent'
import type { AgentSession, AgentMessage, ToolCallStep, AgentTaskRecord } from '@/api/modules/agent'
import { marked } from 'marked'
import hljs from 'highlight.js'
import { markedHighlight } from 'marked-highlight'
import 'highlight.js/styles/github.css'

// marked v18 正确的高亮配置方式
marked.use(markedHighlight({
  langPrefix: 'hljs language-',
  highlight(code: string, lang: string) {
    if (lang && hljs.getLanguage(lang)) {
      try { return hljs.highlight(code, { language: lang }).value } catch { /* */ }
    }
    return hljs.highlightAuto(code).value
  }
}))
marked.setOptions({ breaks: true, gfm: true })

interface Message {
  role: 'user' | 'assistant'
  content: string
  /** 流式打字内容（逐字追加中） */
  typingContent: string
  /** 思考过程文本 */
  thinking: string | null
  /** 思考过程是否展开 */
  thinkingExpanded: boolean
  /** 思考过程是否可见（动画过渡） */
  thinkingVisible: boolean
  /** 工具调用步骤明细 */
  toolSteps: ToolCallStep[] | null
  /** 各工具步骤是否展开 */
  toolOpen: boolean[]
  /** 异步任务进行中的实时进度步骤（时间线渲染） */
  progressSteps: string[]
  timestamp: string
  loading?: boolean
  /** 打字动画是否完成 */
  typingDone: boolean
  /** 本回合消耗的输入 Token */
  inputTokens: number
  /** 本回合消耗的输出 Token */
  outputTokens: number
}

const messages = ref<Message[]>([])
const inputText = ref('')
const sending = ref(false)
const sessionId = ref<string | null>(null)
const sessions = ref<AgentSession[]>([])
const drawerOpen = ref(false)
const messagesRef = ref<HTMLElement | null>(null)
const route = useRoute()

// 打字动画定时器
let typingTimer: ReturnType<typeof setInterval> | null = null

const quickSuggests = [
  { label: '查员工信息', text: '查询员工张三的信息' },
  { label: '发起入职流程', text: '帮我发起一名新员工的入职流程' },
  { label: '查岗位', text: '查询目前开放的岗位' },
  { label: '查绩效', text: '查询本月绩效结果' }
]

// 欢迎态能力卡片（点击即发送）
const capabilities = [
  { icon: UserOutlined, label: '员工查询', text: '查询员工张三的信息' },
  { icon: RobotOutlined, label: '简历分析', text: '帮我分析一份简历' },
  { icon: ApartmentOutlined, label: '发起流程', text: '帮我发起一名新员工的入职流程' },
  { icon: TeamOutlined, label: '岗位查询', text: '查询目前开放的岗位' },
  { icon: TrophyOutlined, label: '绩效查询', text: '查询本月绩效结果' },
  { icon: BookOutlined, label: '培训课程', text: '查询最近的培训课程' }
]

const showWelcome = computed(() => messages.value.length <= 1 && !sending.value)

onMounted(() => {
  loadSessions()
  addWelcomeMessage()
  const q = route.query.q as string | undefined
  if (q) {
    inputText.value = q
    nextTick(() => sendMessage())
  }
})

onBeforeUnmount(() => {
  if (typingTimer) clearInterval(typingTimer)
})

async function loadSessions() {
  try { sessions.value = await getSessions() } catch { /* */ }
}

function addWelcomeMessage() {
  messages.value.push({
    role: 'assistant',
    content: '您好！我是 **HR 智能助手**，可以帮您查询员工信息、岗位信息、简历信息，发起入职/离职/调岗流程，查询绩效结果、培训课程等。\n\n请问有什么可以帮您的？',
    typingContent: '',
    thinking: null,
    thinkingExpanded: false,
    thinkingVisible: false,
    toolSteps: null,
    toolOpen: [],
    progressSteps: [],
    timestamp: formatTime(new Date()),
    typingDone: true,
    inputTokens: 0,
    outputTokens: 0
  })
}

function formatTime(date: Date): string {
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function formatDate(dt: string): string {
  const d = new Date(dt)
  const now = new Date()
  if (d.toDateString() === now.toDateString()) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return `${d.getMonth() + 1}/${d.getDate()} ${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}

function scrollToBottom() {
  nextTick(() => {
    const container = messagesRef.value
    if (container) container.scrollTop = container.scrollHeight
  })
}

/** 渲染 Markdown 为 HTML */
function renderMarkdown(text: string): string {
  try { return marked.parse(text) as string } catch { return text }
}

/** 工具参数美化：JSON 格式化，失败则原文 */
function formatArgs(args: string): string {
  if (!args) return '{}'
  try { return JSON.stringify(JSON.parse(args), null, 2) } catch { return args }
}

// ============ 发送消息 ============

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || sending.value) return

  inputText.value = ''
  sending.value = true
  if (typingTimer) { clearInterval(typingTimer); typingTimer = null }

  messages.value.push({
    role: 'user',
    content: text,
    typingContent: '',
    thinking: null,
    thinkingExpanded: false,
    thinkingVisible: false,
    toolSteps: null,
    toolOpen: [],
    progressSteps: [],
    timestamp: formatTime(new Date()),
    typingDone: true,
    inputTokens: 0,
    outputTokens: 0
  })
  scrollToBottom()

  const aiMsgIndex = messages.value.length
  messages.value.push({
    role: 'assistant',
    content: '',
    typingContent: '',
    thinking: null,
    thinkingExpanded: false,
    thinkingVisible: false,
    toolSteps: null,
    toolOpen: [],
    progressSteps: [],
    timestamp: formatTime(new Date()),
    loading: true,
    typingDone: false,
    inputTokens: 0,
    outputTokens: 0
  })
  scrollToBottom()

  try {
    // 异步提交任务，立即拿到 taskId，不再长时间占用 HTTP 连接
    const submitted = await agentChatAsync({
      message: text,
      sessionId: sessionId.value || undefined
    })
    sessionId.value = submitted.sessionId

    // 轮询任务直至终态，期间实时刷新进度步骤
    const task = await pollTask(submitted.taskId)

    const msg = messages.value[aiMsgIndex]
    msg.loading = false
    msg.progressSteps = task.steps || []

    const result = task.result
    if (task.status !== 'SUCCEEDED' || !result) {
      throw new Error(task.error || '任务处理失败')
    }

    msg.content = result.response
    msg.thinking = result.thinking || null
    msg.thinkingVisible = !!result.thinking
    msg.toolSteps = result.toolSteps || null
    msg.toolOpen = (result.toolSteps || []).map(() => false)
    msg.inputTokens = result.inputTokens || 0
    msg.outputTokens = result.outputTokens || 0

    // 思考过程默认折叠，用户点击展开

    // 启动打字动画
    startTyping(aiMsgIndex)
    loadSessions()
  } catch (e: any) {
    messages.value[aiMsgIndex].loading = false
    messages.value[aiMsgIndex].content = '抱歉，请求处理失败：' + (e?.message || '未知错误')
    messages.value[aiMsgIndex].typingDone = true
  } finally {
    sending.value = false
  }
}

/** 轮询异步任务直至终态，每轮刷新进行中消息的进度步骤 */
async function pollTask(taskId: string, maxPoll = 400): Promise<AgentTaskRecord> {
  for (let i = 0; i < maxPoll; i++) {
    await sleep(900)
    const task = await getAgentTask(taskId)
    const lastMsg = messages.value[messages.value.length - 1]
    if (lastMsg?.role === 'assistant' && lastMsg.loading && task.steps?.length) {
      lastMsg.progressSteps = task.steps
      scrollToBottom()
    }
    if (task.status !== 'RUNNING') return task
  }
  throw new Error('任务轮询超时')
}

function sleep(ms: number) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

/** 打字动画：逐字显示回答内容 */
function startTyping(msgIndex: number) {
  const msg = messages.value[msgIndex]
  if (!msg.content) {
    msg.typingDone = true
    return
  }

  if (typingTimer) clearInterval(typingTimer)

  let idx = 0
  msg.typingContent = ''
  msg.typingDone = false

  typingTimer = setInterval(() => {
    if (idx < msg.content.length) {
      msg.typingContent = msg.content.slice(0, idx + 1)
      idx++
      scrollToBottom()
    } else {
      msg.typingContent = ''
      msg.typingDone = true
      if (typingTimer) { clearInterval(typingTimer); typingTimer = null }
    }
  }, 18) // 约 55 字/秒，自然打字速度
}

function toggleThinking(index: number) {
  const msg = messages.value[index]
  if (msg.thinking) msg.thinkingExpanded = !msg.thinkingExpanded
}

function toggleToolStep(msgIndex: number, stepIndex: number) {
  const msg = messages.value[msgIndex]
  if (msg.toolOpen) msg.toolOpen[stepIndex] = !msg.toolOpen[stepIndex]
}

async function copyMessage(index: number) {
  const msg = messages.value[index]
  if (!msg.content) return
  try {
    await navigator.clipboard.writeText(msg.content)
    message.success('已复制')
  } catch {
    message.error('复制失败')
  }
}

function suggest(text: string) {
  inputText.value = text
  sendMessage()
}

async function newConversation() {
  if (typingTimer) { clearInterval(typingTimer); typingTimer = null }
  sessionId.value = null
  messages.value = []
  addWelcomeMessage()
  drawerOpen.value = false
}

async function selectSession(session: AgentSession) {
  if (sessionId.value === session.sessionId) return
  if (typingTimer) { clearInterval(typingTimer); typingTimer = null }
  sessionId.value = session.sessionId
  messages.value = []
  sending.value = true

  try {
    const msgs = await getSessionMessages(session.sessionId)
    for (const m of msgs) {
      if (m.role === 'user' || m.role === 'assistant') {
        messages.value.push({
          role: m.role as 'user' | 'assistant',
          content: m.content,
          typingContent: '',
          thinking: null,
          thinkingExpanded: false,
          thinkingVisible: false,
          toolSteps: null,
          toolOpen: [],
          progressSteps: [],
          timestamp: formatTime(new Date(m.createTime)),
          typingDone: true,
          inputTokens: 0,
          outputTokens: 0
        })
      }
    }
  } catch {
    message.error('加载历史消息失败')
    addWelcomeMessage()
  } finally {
    sending.value = false
    drawerOpen.value = false
    scrollToBottom()
  }
}

async function deleteSession(session: AgentSession) {
  try {
    await agentClearSession(session.sessionId)
    sessions.value = sessions.value.filter(s => s.sessionId !== session.sessionId)
    if (sessionId.value === session.sessionId) newConversation()
    message.success('已删除')
  } catch {
    message.error('删除失败')
  }
}

async function clearConversation() {
  if (sessionId.value) {
    try {
      await agentClearSession(sessionId.value)
      sessions.value = sessions.value.filter(s => s.sessionId !== sessionId.value)
    } catch { /* */ }
  }
  newConversation()
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}
</script>

<template>
  <div class="chat-page">
    <!-- 历史会话抽屉（深色，DeepSeek 网页版风格） -->
    <Drawer v-model:open="drawerOpen" placement="left" :width="300" :closable="false" class="session-drawer">
      <div class="drawer-body">
        <div class="drawer-header">
          <div class="drawer-title">
            <HistoryOutlined class="drawer-title-icon" />
            <span>历史会话</span>
          </div>
          <Button type="primary" size="small" ghost @click="newConversation">
            <PlusOutlined />
            <span>新对话</span>
          </Button>
        </div>
        <div v-if="sessions.length === 0" class="drawer-empty"><span>暂无历史记录</span></div>
        <div v-else class="drawer-list">
          <div v-for="session in sessions" :key="session.sessionId"
            class="session-item"
            :class="{ active: session.sessionId === sessionId }"
            @click="selectSession(session)">
            <div class="session-info">
              <div class="session-label">{{ session.title || `会话 ${session.id}` }}</div>
              <div class="session-meta">{{ formatDate(session.createTime) }}</div>
            </div>
            <DeleteOutlined class="session-delete" @click.stop="deleteSession(session)" />
          </div>
        </div>
      </div>
    </Drawer>

    <!-- 主聊天区域 -->
    <div class="chat-main">
      <div class="chat-header">
        <div class="header-left">
          <Tooltip title="历史会话">
            <Button type="text" class="history-btn" @click="drawerOpen = true">
              <HistoryOutlined />
            </Button>
          </Tooltip>
          <RobotOutlined class="header-icon" />
          <span class="header-title">HR 智能助手</span>
          <Tag color="blue" class="header-tag">AI</Tag>
          <span v-if="sending" class="thinking-badge">
            <span class="pulse-dot"></span>
            <span>思考中...</span>
          </span>
        </div>
        <div class="header-actions">
          <Tooltip title="新对话">
            <Button type="text" size="small" @click="newConversation"><PlusOutlined /></Button>
          </Tooltip>
          <Tooltip title="清除">
            <Button type="text" size="small" @click="clearConversation"><ClearOutlined /></Button>
          </Tooltip>
        </div>
      </div>

      <!-- 消息区域（居中列） -->
      <div class="chat-messages" ref="messagesRef">
        <div class="chat-column">
          <!-- 欢迎页 -->
          <div v-if="showWelcome" class="welcome-hero">
            <div class="welcome-bot"><RobotOutlined /></div>
            <div class="welcome-title">你好，我是 HR 智能助手</div>
            <div class="welcome-sub">查询员工、岗位、简历与绩效，发起入职/离职/调岗流程，一切人力事务都可以问我</div>
            <div class="capability-grid">
              <div v-for="cap in capabilities" :key="cap.text" class="capability-card" @click="suggest(cap.text)">
                <component :is="cap.icon" class="capability-icon" />
                <div class="capability-info">
                  <div class="capability-label">{{ cap.label }}</div>
                  <div class="capability-text">{{ cap.text }}</div>
                </div>
              </div>
            </div>
            <div class="welcome-suggests">
              <div v-for="s in quickSuggests" :key="s.text" class="welcome-chip" @click="suggest(s.text)">
                <span class="chip-label">{{ s.label }}</span>
                <span class="chip-text">{{ s.text }}</span>
              </div>
            </div>
          </div>

          <!-- 消息列表 -->
          <div v-for="(msg, index) in messages" :key="index" class="message-row" :class="msg.role">
            <!-- 头像 -->
            <div class="avatar" :class="msg.role">
              <UserOutlined v-if="msg.role === 'user'" />
              <RobotOutlined v-else />
            </div>

            <div class="bubble-group">
              <!-- 思考过程 -->
              <div v-if="msg.role === 'assistant' && msg.thinking"
                class="thinking-section"
                :class="{ expanded: msg.thinkingExpanded, visible: msg.thinkingVisible }"
                @click="toggleThinking(index)">
                <div class="thinking-header">
                  <BulbOutlined class="thinking-icon" />
                  <span class="thinking-label">推理过程</span>
                  <span class="thinking-arrow-wrap">
                    <CaretRightOutlined v-if="!msg.thinkingExpanded" class="thinking-arrow" />
                    <CaretDownOutlined v-else class="thinking-arrow" />
                  </span>
                </div>
                <div class="thinking-body">
                  <div class="thinking-text">{{ msg.thinking }}</div>
                </div>
              </div>

              <!-- 工具调用步骤 -->
              <div v-if="msg.role === 'assistant' && msg.toolSteps && msg.toolSteps.length"
                class="tool-steps">
                <div v-for="(step, si) in msg.toolSteps" :key="si"
                  class="tool-step"
                  :class="{ expanded: msg.toolOpen[si] }">
                  <div class="tool-step-header" @click="toggleToolStep(index, si)">
                    <ToolOutlined class="tool-step-icon" />
                    <span class="tool-step-name">调用工具：{{ step.toolName }}</span>
                    <span class="tool-step-arrow-wrap">
                      <CaretRightOutlined v-if="!msg.toolOpen[si]" class="tool-step-arrow" />
                      <CaretDownOutlined v-else class="tool-step-arrow" />
                    </span>
                  </div>
                  <div class="tool-step-body">
                    <pre class="tool-step-args">{{ formatArgs(step.arguments) }}</pre>
                  </div>
                </div>
              </div>

              <!-- 气泡 -->
              <div class="bubble" :class="msg.role">
                <div class="bubble-content">
                  <!-- loading：异步任务执行中，展示实时进度步骤 -->
                  <div v-if="msg.loading" class="bubble-loading">
                    <div class="loading-row">
                      <Spin size="small" class="loading-spin" />
                      <span class="loading-text">正在处理...</span>
                    </div>
                    <div v-if="msg.progressSteps?.length" class="progress-list">
                      <div v-for="(step, si) in msg.progressSteps" :key="si" class="progress-item">
                        <span class="progress-dot"></span>
                        <span class="progress-text">{{ step }}</span>
                      </div>
                    </div>
                  </div>
                  <!-- 打字动画中 -->
                  <div v-else-if="!msg.typingDone && msg.typingContent" class="bubble-text markdown-body">
                    <span v-html="renderMarkdown(msg.typingContent)"></span>
                    <span class="typing-cursor">|</span>
                  </div>
                  <!-- 完成 -->
                  <div v-else class="bubble-text markdown-body" v-html="renderMarkdown(msg.content)"></div>
                </div>
                <div v-if="msg.typingDone && msg.content" class="bubble-time">{{ msg.timestamp }}</div>
                <!-- Token 消耗角标 -->
                <div v-if="msg.role === 'assistant' && msg.typingDone && msg.content && (msg.inputTokens > 0 || msg.outputTokens > 0)" class="token-badge">
                  <span>输入 {{ msg.inputTokens.toLocaleString() }}</span>
                  <span class="token-sep">|</span>
                  <span>输出 {{ msg.outputTokens.toLocaleString() }}</span>
                </div>
              </div>

              <!-- 消息操作：复制 -->
              <div v-if="msg.role === 'assistant' && msg.typingDone && msg.content" class="bubble-actions">
                <Tooltip title="复制">
                  <Button type="text" size="small" class="msg-action-btn" @click="copyMessage(index)">
                    <CopyOutlined />
                  </Button>
                </Tooltip>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="chat-input-area">
        <div class="input-wrapper">
          <div class="chat-input-card">
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
          <div v-if="showWelcome" class="input-suggests">
            <span v-for="s in quickSuggests" :key="s.text" class="input-chip" @click="suggest(s.text)">{{ s.label }}</span>
          </div>
          <div class="input-hint">按 Enter 发送，Shift+Enter 换行</div>
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
  background: var(--bg-primary);
  overflow: hidden;
}

/* 主聊天区域 */
.chat-main { flex: 1; display: flex; flex-direction: column; background: transparent; overflow: hidden; }
.chat-header { display: flex; align-items: center; justify-content: space-between; padding: 12px 24px; border-bottom: 1px solid var(--border); background: var(--bg-secondary); flex-shrink: 0; }
.header-left { display: flex; align-items: center; gap: 10px; }
.header-actions { display: flex; gap: 4px; }
.history-btn { margin-left: -8px; }
.header-icon { font-size: 20px; color: var(--accent); }
.header-title { font-size: 15px; font-weight: 600; color: var(--text-primary); }
.header-tag { font-size: 11px; line-height: 18px; }
.thinking-badge { display: inline-flex; align-items: center; gap: 5px; font-size: 12px; color: #c98a2e; padding: 2px 10px; background: #fdf6e8; border-radius: 10px; }
.pulse-dot { width: 6px; height: 6px; border-radius: 50%; background: #c98a2e; animation: pulse 1.2s ease-in-out infinite; }
@keyframes pulse { 0%,100% { opacity: 1; transform: scale(1); } 50% { opacity: 0.3; transform: scale(0.7); } }

/* 消息区域（居中列） */
.chat-messages { flex: 1; overflow-y: auto; padding: 20px 24px 0; display: flex; scroll-behavior: smooth; }
.chat-column { max-width: 820px; margin: 0 auto; width: 100%; display: flex; flex-direction: column; gap: 16px; }
.message-row { display: flex; gap: 10px; }
.message-row.user { flex-direction: row-reverse; }
.message-row.assistant { align-self: flex-start; }

/* 欢迎区 */
.welcome-hero { display: flex; flex-direction: column; align-items: center; text-align: center; padding: 36px 24px 20px; }
.welcome-bot { width: 60px; height: 60px; border-radius: 16px; background: linear-gradient(135deg, var(--accent), var(--accent-hover)); color: #fff; font-size: 26px; display: flex; align-items: center; justify-content: center; box-shadow: 0 8px 20px rgba(74,127,193,0.25); margin-bottom: 14px; }
.welcome-title { font-size: 18px; font-weight: 600; color: var(--text-primary); }
.welcome-sub { font-size: 13px; color: var(--text-tertiary); margin: 6px 0 22px; }
.capability-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; width: 100%; max-width: 640px; }
.capability-card { display: flex; align-items: center; gap: 10px; background: var(--bg-secondary); border: 1px solid var(--border); border-radius: var(--radius-lg); padding: 12px 14px; cursor: pointer; transition: all 0.15s; text-align: left; }
.capability-card:hover { border-color: var(--accent-border); background: var(--accent-light); transform: translateY(-1px); box-shadow: var(--shadow-md); }
.capability-icon { font-size: 18px; color: var(--accent); flex-shrink: 0; }
.capability-info { min-width: 0; }
.capability-label { font-size: 13px; font-weight: 600; color: var(--text-primary); }
.capability-text { font-size: 11px; color: var(--text-tertiary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.welcome-suggests { display: flex; flex-wrap: wrap; gap: 10px; justify-content: center; margin-top: 16px; }
.welcome-chip { display: flex; align-items: center; gap: 6px; background: var(--bg-secondary); border: 1px solid var(--border); border-radius: 20px; padding: 6px 14px; cursor: pointer; transition: all 0.15s; }
.welcome-chip:hover { border-color: var(--accent-border); background: var(--accent-light); }
.chip-label { font-size: 12px; font-weight: 600; color: var(--accent); }
.chip-text { font-size: 12px; color: var(--text-secondary); }

.avatar { width: 34px; height: 34px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 16px; flex-shrink: 0; }
.avatar.user { background: linear-gradient(135deg, var(--accent), var(--accent-hover)); color: #fff; }
.avatar.assistant { background: linear-gradient(135deg, #2f3742, #1b2029); color: #fff; }

.bubble-group { display: flex; flex-direction: column; gap: 6px; min-width: 0; max-width: 100%; }

/* 思考过程 */
.thinking-section { background: var(--bg-secondary); border: 1px solid var(--border); border-radius: var(--radius-md); overflow: hidden; cursor: pointer; transition: all 0.25s ease; max-height: 32px; opacity: 0.75; }
.thinking-section.visible { opacity: 1; }
.thinking-section.expanded { max-height: 600px; overflow-y: auto; }
.thinking-section:hover { border-color: #d0d5dd; background: #fafbfc; }
.thinking-header { display: flex; align-items: center; gap: 6px; padding: 6px 10px; font-size: 12px; color: var(--text-tertiary); user-select: none; }
.thinking-section.expanded .thinking-header { border-bottom: 1px solid var(--border-light); }
.thinking-icon { font-size: 12px; color: #c98a2e; flex-shrink: 0; }
.thinking-label { font-weight: 500; flex: 1; }
.thinking-arrow-wrap { flex-shrink: 0; display: flex; align-items: center; }
.thinking-arrow { font-size: 10px; color: var(--text-quaternary); }
.thinking-body { padding: 8px 10px; max-height: 500px; overflow-y: auto; }
.thinking-text { font-size: 11px; color: var(--text-secondary); line-height: 1.6; white-space: pre-wrap; word-break: break-word; }

/* 工具调用步骤 */
.tool-steps { display: flex; flex-direction: column; gap: 6px; }
.tool-step { background: var(--bg-secondary); border: 1px solid var(--border); border-radius: var(--radius-md); overflow: hidden; transition: all 0.2s; }
.tool-step:hover { border-color: #d0d5dd; }
.tool-step-header { display: flex; align-items: center; gap: 6px; padding: 7px 10px; font-size: 12px; cursor: pointer; user-select: none; color: var(--text-secondary); }
.tool-step-icon { font-size: 12px; color: var(--accent); flex-shrink: 0; }
.tool-step-name { font-weight: 500; flex: 1; }
.tool-step-arrow-wrap { flex-shrink: 0; display: flex; align-items: center; }
.tool-step-arrow { font-size: 10px; color: var(--text-quaternary); }
.tool-step-body { padding: 8px 12px; border-top: 1px solid var(--border-light); background: #fafbfc; max-height: 320px; overflow-y: auto; display: none; }
.tool-step.expanded .tool-step-body { display: block; }
.tool-step-args { margin: 0; font-size: 12px; line-height: 1.6; color: var(--text-secondary); font-family: var(--font-num); white-space: pre-wrap; word-break: break-word; }

/* 气泡 */
.bubble { padding: 10px 14px; border-radius: var(--radius-lg); position: relative; }
.bubble.user { background: var(--accent); color: #fff; border-bottom-right-radius: 4px; }
.bubble.assistant { background: var(--bg-secondary); border: 1px solid var(--border); color: var(--text-primary); border-bottom-left-radius: 4px; }
.bubble-content { font-size: 14px; line-height: 1.65; word-break: break-word; white-space: pre-wrap; }
.bubble-text { font-size: 14px; line-height: 1.65; word-break: break-word; white-space: pre-wrap; }
.bubble-time { font-size: 11px; color: var(--text-quaternary); margin-top: 4px; text-align: right; font-family: var(--font-num); }
.bubble.user .bubble-time { color: rgba(255,255,255,0.65); }
/* Token 消耗角标 */
.token-badge { font-size: 10px; color: var(--text-quaternary); margin-top: 2px; text-align: right; display: flex; justify-content: flex-end; gap: 4px; font-family: var(--font-num); }
.token-sep { color: var(--text-quaternary); }
.loading-spin { display: block; margin: 4px 0; }

/* 异步任务进行中的实时进度 */
.bubble-loading { display: flex; flex-direction: column; gap: 8px; }
.loading-row { display: flex; align-items: center; gap: 8px; }
.loading-text { font-size: 12px; color: var(--text-tertiary); }
.progress-list { display: flex; flex-direction: column; gap: 4px; padding-left: 2px; }
.progress-item { display: flex; align-items: flex-start; gap: 6px; font-size: 12px; color: var(--text-secondary); line-height: 1.5; }
.progress-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--accent); flex-shrink: 0; margin-top: 5px; animation: pulse 1.2s ease-in-out infinite; }
.progress-text { word-break: break-all; white-space: pre-wrap; }

/* 消息操作 */
.bubble-actions { display: flex; gap: 4px; opacity: 0; transition: opacity 0.15s; }
.message-row:hover .bubble-actions { opacity: 1; }
.msg-action-btn { color: var(--text-quaternary); }

/* 打字光标 */
.typing-cursor { display: inline-block; font-size: 14px; color: var(--accent); font-weight: 300; animation: blink 0.8s step-end infinite; margin-left: 1px; }
@keyframes blink { 0%,100% { opacity: 1; } 50% { opacity: 0; } }

/* Markdown 渲染 */
.markdown-body { line-height: 1.7; font-size: 14px; }
.markdown-body :deep(p) { margin: 0 0 8px; }
.markdown-body :deep(p:last-child) { margin-bottom: 0; }
.markdown-body :deep(strong) { font-weight: 600; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 20px; margin: 6px 0; }
.markdown-body :deep(li) { margin: 2px 0; }
.markdown-body :deep(code) { background: var(--bg-tertiary); padding: 1px 5px; border-radius: 4px; font-size: 12px; color: var(--accent-pressed); font-family: var(--font-num); }
.bubble.user .markdown-body :deep(code) { background: rgba(255,255,255,0.2); color: #fff; }
.markdown-body :deep(pre) { background: #f7f9fb; border: 1px solid var(--border-light); border-radius: var(--radius-md); padding: 12px 14px; overflow-x: auto; margin: 8px 0; }
.markdown-body :deep(pre code) { background: none; padding: 0; color: var(--text-primary); font-size: 12px; line-height: 1.5; }
.markdown-body :deep(table) { border-collapse: collapse; width: 100%; margin: 8px 0; font-size: 13px; }
.markdown-body :deep(th), .markdown-body :deep(td) { border: 1px solid var(--border); padding: 6px 10px; text-align: left; }
.markdown-body :deep(th) { background: var(--bg-tertiary); font-weight: 600; }
.markdown-body :deep(blockquote) { border-left: 3px solid var(--accent); padding: 6px 12px; margin: 8px 0; background: var(--accent-light); color: var(--text-secondary); font-size: 13px; border-radius: 0 var(--radius-sm) var(--radius-sm) 0; }
.markdown-body :deep(h1), .markdown-body :deep(h2), .markdown-body :deep(h3) { margin: 12px 0 6px; font-weight: 600; }
.markdown-body :deep(h1) { font-size: 18px; }
.markdown-body :deep(h2) { font-size: 16px; }
.markdown-body :deep(h3) { font-size: 14px; }
.markdown-body :deep(a) { color: var(--accent); text-decoration: none; }
.markdown-body :deep(a:hover) { text-decoration: underline; }
.markdown-body :deep(.hljs) { background: #f7f9fb; padding: 0; }

/* 输入区域 */
.chat-input-area { padding: 12px 24px 16px; border-top: 1px solid var(--border); flex-shrink: 0; background: var(--bg-primary); }
.input-wrapper { max-width: 820px; margin: 0 auto; }
.chat-input-card { position: relative; background: var(--bg-secondary); border: 1px solid var(--border); border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); padding: 6px 8px 6px 12px; transition: box-shadow 0.2s, border-color 0.2s; display: flex; align-items: flex-end; gap: 8px; }
.chat-input-card:focus-within { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-ring); }
.chat-input-card :deep(.chat-input) { background: transparent; border: none; box-shadow: none; resize: none; padding: 4px 0; font-size: 14px; flex: 1; }
.chat-input-card :deep(.chat-input:focus) { border: none; box-shadow: none; }
.chat-input-card :deep(.chat-input textarea) { font-size: 14px; line-height: 1.6; }
.send-btn { flex-shrink: 0; margin-bottom: 4px; }
.input-suggests { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.input-chip { font-size: 11px; color: var(--accent); background: var(--accent-light); border-radius: 14px; padding: 3px 10px; cursor: pointer; transition: all 0.15s; }
.input-chip:hover { background: var(--accent-soft); }
.input-hint { font-size: 11px; color: var(--text-quaternary); margin-top: 6px; text-align: right; }

/* 历史会话抽屉（深色） */
.session-drawer :deep(.ant-drawer-content) { background: var(--sidebar-bg); }
.session-drawer :deep(.ant-drawer-body) { padding: 0; }
.drawer-body { display: flex; flex-direction: column; height: 100%; }
.drawer-header { display: flex; align-items: center; justify-content: space-between; padding: 16px; border-bottom: 1px solid var(--sidebar-border); flex-shrink: 0; }
.drawer-title { display: flex; align-items: center; gap: 8px; font-size: 14px; font-weight: 600; color: var(--sidebar-text); }
.drawer-title-icon { font-size: 16px; color: var(--sidebar-text-muted); }
.drawer-empty { display: flex; align-items: center; justify-content: center; flex: 1; color: var(--sidebar-text-muted); font-size: 13px; }
.drawer-list { flex: 1; overflow-y: auto; padding: 8px; }
.session-item { display: flex; align-items: center; justify-content: space-between; padding: 10px 12px; border-radius: 8px; cursor: pointer; transition: all 0.2s; }
.session-item:hover { background: var(--sidebar-hover); }
.session-item.active { background: var(--sidebar-active); }
.session-info { flex: 1; min-width: 0; }
.session-label { font-size: 13px; color: var(--sidebar-text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.session-meta { font-size: 11px; color: var(--sidebar-text-muted); margin-top: 2px; }
.session-delete { font-size: 12px; color: var(--sidebar-text-muted); opacity: 0; transition: opacity 0.15s; flex-shrink: 0; }
.session-item:hover .session-delete { opacity: 1; }
.session-delete:hover { color: #ff4d4f; }
</style>
