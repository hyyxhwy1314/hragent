<script setup lang="ts">
import { ref, nextTick, onMounted, computed, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { Input, Button, Spin, Tag, Tooltip, message } from 'ant-design-vue'
import {
  SendOutlined, ClearOutlined, RobotOutlined, UserOutlined,
  PlusOutlined, DeleteOutlined, HistoryOutlined, BulbOutlined,
  CaretRightOutlined, CaretDownOutlined
} from '@ant-design/icons-vue'
import { agentChat, agentContinueChat, agentClearSession, getSessions, getSessionMessages } from '@/api/modules/agent'
import type { AgentSession, AgentMessage } from '@/api/modules/agent'
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
    timestamp: formatTime(new Date()),
    typingDone: true
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
    timestamp: formatTime(new Date()),
    loading: true,
    typingDone: false
  })
  scrollToBottom()

  try {
    let result
    if (sessionId.value) {
      result = await agentContinueChat(sessionId.value, { message: text })
    } else {
      result = await agentChat({ message: text })
      sessionId.value = result.sessionId
    }

    const msg = messages.value[aiMsgIndex]
    msg.loading = false
    msg.content = result.response
    msg.thinking = result.thinking || null
    msg.thinkingVisible = !!result.thinking
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

function suggest(text: string) {
  inputText.value = text
  sendMessage()
}

async function newConversation() {
  if (typingTimer) { clearInterval(typingTimer); typingTimer = null }
  sessionId.value = null
  messages.value = []
  addWelcomeMessage()
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
          timestamp: formatTime(new Date(m.createTime)),
          typingDone: true
        })
      }
    }
  } catch {
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
    <div class="chat-layout">
      <!-- 历史会话侧边栏 -->
      <div class="history-sidebar">
        <div class="sidebar-header">
          <HistoryOutlined class="sidebar-icon" />
          <span class="sidebar-title">历史对话</span>
        </div>
        <div class="sidebar-body">
          <div v-if="sessions.length === 0" class="sidebar-empty"><span>暂无历史记录</span></div>
          <div v-else class="session-list">
            <div v-for="session in sessions" :key="session.sessionId"
              class="session-item"
              :class="{ active: session.sessionId === sessionId }"
              @click="selectSession(session)">
              <div class="session-info">
                <div class="session-label">{{ session.title || `会话 ${session.id}` }}</div>
                <div class="session-meta">
                  <span class="session-time">{{ formatDate(session.createTime) }}</span>
                </div>
              </div>
              <DeleteOutlined class="session-delete" @click.stop="deleteSession(session)" />
            </div>
          </div>
        </div>
      </div>

      <!-- 主聊天区域 -->
      <div class="chat-main">
        <div class="chat-header">
          <div class="header-left">
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

        <!-- 消息区域 -->
        <div class="chat-messages" ref="messagesRef">
          <!-- 欢迎页 -->
          <div v-if="showWelcome" class="welcome-hero">
            <div class="welcome-bot"><RobotOutlined /></div>
            <div class="welcome-title">HR 智能助手</div>
            <div class="welcome-sub">查询员工、岗位、简历与绩效，发起入职/离职/调岗流程</div>
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

              <!-- 气泡 -->
              <div class="bubble" :class="msg.role">
                <div class="bubble-content">
                  <!-- loading -->
                  <Spin v-if="msg.loading" size="small" class="loading-spin" />
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
          </div>
          <div class="input-hint">按 Enter 发送，Shift+Enter 换行</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-page {
  height: calc(100vh - 56px);
  display: flex;
  flex-direction: column;
  background: #f7f8fa;
  overflow: hidden;
}
.chat-layout { display: flex; height: 100%; overflow: hidden; }

/* 侧边栏 */
.history-sidebar { width: 240px; min-width: 240px; background: #fff; border-right: 1px solid #e5e7eb; display: flex; flex-direction: column; overflow: hidden; }
.sidebar-header { display: flex; align-items: center; gap: 8px; padding: 14px 16px; border-bottom: 1px solid #f0f0f0; flex-shrink: 0; }
.sidebar-icon { font-size: 16px; color: rgba(0,0,0,0.45); }
.sidebar-title { font-size: 14px; font-weight: 600; color: rgba(0,0,0,0.85); }
.sidebar-body { flex: 1; overflow-y: auto; padding: 8px; }
.sidebar-empty { display: flex; align-items: center; justify-content: center; height: 100%; color: rgba(0,0,0,0.35); font-size: 13px; }
.session-list { display: flex; flex-direction: column; gap: 4px; }
.session-item { display: flex; align-items: center; justify-content: space-between; padding: 10px 12px; border-radius: 6px; cursor: pointer; transition: all 0.2s; }
.session-item:hover { background: #e8ecf1; }
.session-item.active { background: #e8f0fe; }
.session-info { flex: 1; min-width: 0; }
.session-label { font-size: 13px; color: rgba(0,0,0,0.85); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.session-meta { font-size: 11px; color: rgba(0,0,0,0.35); margin-top: 2px; }
.session-delete { font-size: 12px; color: rgba(0,0,0,0.25); opacity: 0; transition: opacity 0.15s; flex-shrink: 0; }
.session-item:hover .session-delete { opacity: 1; }
.session-delete:hover { color: #ff4d4f; }

/* 主聊天区域 */
.chat-main { flex: 1; display: flex; flex-direction: column; background: #fff; overflow: hidden; }
.chat-header { display: flex; align-items: center; justify-content: space-between; padding: 14px 24px; border-bottom: 1px solid #e5e7eb; flex-shrink: 0; }
.header-left { display: flex; align-items: center; gap: 10px; }
.header-actions { display: flex; gap: 4px; }
.header-icon { font-size: 20px; color: #4a7fc1; }
.header-title { font-size: 15px; font-weight: 600; color: rgba(0,0,0,0.85); }
.header-tag { font-size: 11px; line-height: 18px; }
.thinking-badge { display: inline-flex; align-items: center; gap: 5px; font-size: 12px; color: #fa8c16; padding: 2px 10px; background: #fff7e6; border-radius: 10px; }
.pulse-dot { width: 6px; height: 6px; border-radius: 50%; background: #fa8c16; animation: pulse 1.2s ease-in-out infinite; }
@keyframes pulse { 0%,100% { opacity: 1; transform: scale(1); } 50% { opacity: 0.3; transform: scale(0.7); } }

/* 消息区域 */
.chat-messages { flex: 1; overflow-y: auto; padding: 20px 24px; display: flex; flex-direction: column; gap: 16px; scroll-behavior: smooth; }
.message-row { display: flex; gap: 10px; max-width: 78%; }
.message-row.user { align-self: flex-end; flex-direction: row-reverse; }
.message-row.assistant { align-self: flex-start; }

/* 欢迎区 */
.welcome-hero { display: flex; flex-direction: column; align-items: center; text-align: center; padding: 40px 24px 24px; }
.welcome-bot { width: 64px; height: 64px; border-radius: 50%; background: linear-gradient(135deg,#4a7fc1,#7ba3d0); color: #fff; font-size: 28px; display: flex; align-items: center; justify-content: center; box-shadow: 0 8px 20px rgba(74,127,193,0.25); margin-bottom: 16px; }
.welcome-title { font-size: 18px; font-weight: 600; color: rgba(0,0,0,0.88); }
.welcome-sub { font-size: 13px; color: #6b7280; margin: 6px 0 20px; }
.welcome-suggests { display: flex; flex-wrap: wrap; gap: 10px; justify-content: center; }
.welcome-chip { display: flex; align-items: center; gap: 6px; background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; padding: 8px 14px; cursor: pointer; transition: all 0.15s; }
.welcome-chip:hover { border-color: #4a7fc1; background: #e8f0fe; transform: translateY(-1px); }
.chip-label { font-size: 12px; font-weight: 600; color: #4a7fc1; }
.chip-text { font-size: 12px; color: rgba(0,0,0,0.65); }

.avatar { width: 34px; height: 34px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 16px; flex-shrink: 0; }
.avatar.user { background: linear-gradient(135deg,#4a7fc1,#6d9ed0); color: #fff; }
.avatar.assistant { background: linear-gradient(135deg,#13C2C2,#36CFC9); color: #fff; }

.bubble-group { display: flex; flex-direction: column; gap: 6px; min-width: 0; }

/* 思考过程 */
.thinking-section { background: #f9fafb; border: 1px solid #e8e8e8; border-radius: 8px; overflow: hidden; cursor: pointer; transition: all 0.25s ease; max-height: 32px; opacity: 0.7; }
.thinking-section.visible { opacity: 1; }
.thinking-section.expanded { max-height: 600px; overflow-y: auto; }
.thinking-section:hover { border-color: #d0d5dd; background: #f5f6f8; }
.thinking-header { display: flex; align-items: center; gap: 6px; padding: 6px 10px; font-size: 12px; color: #8c8c8c; user-select: none; }
.thinking-section.expanded .thinking-header { border-bottom: 1px solid #e8e8e8; }
.thinking-icon { font-size: 12px; color: #fa8c16; flex-shrink: 0; }
.thinking-label { font-weight: 500; flex: 1; }
.thinking-arrow-wrap { flex-shrink: 0; display: flex; align-items: center; }
.thinking-arrow { font-size: 10px; color: #bfbfbf; }
.thinking-body { padding: 8px 10px; max-height: 500px; overflow-y: auto; }
.thinking-text { font-size: 11px; color: #595959; line-height: 1.6; white-space: pre-wrap; word-break: break-word; }

/* 气泡 */
.bubble { padding: 10px 14px; border-radius: 12px; position: relative; }
.bubble.user { background: #4a7fc1; color: #fff; border-bottom-right-radius: 4px; }
.bubble.assistant { background: #f1f3f5; color: rgba(0,0,0,0.85); border-bottom-left-radius: 4px; }
.bubble-content { font-size: 14px; line-height: 1.6; word-break: break-word; white-space: pre-wrap; }
.bubble-text { font-size: 14px; line-height: 1.6; word-break: break-word; white-space: pre-wrap; }
.bubble-time { font-size: 11px; color: rgba(0,0,0,0.35); margin-top: 4px; text-align: right; }
.bubble.user .bubble-time { color: rgba(255,255,255,0.6); }
/* Token 消耗角标 */
.token-badge { font-size: 10px; color: rgba(0,0,0,0.35); margin-top: 2px; text-align: right; display: flex; justify-content: flex-end; gap: 4px; }
.token-sep { color: rgba(0,0,0,0.15); }
.loading-spin { display: block; margin: 4px 0; }

/* 打字光标 */
.typing-cursor { display: inline-block; font-size: 14px; color: #4a7fc1; font-weight: 300; animation: blink 0.8s step-end infinite; margin-left: 1px; }
@keyframes blink { 0%,100% { opacity: 1; } 50% { opacity: 0; } }

/* Markdown 渲染 */
.markdown-body { line-height: 1.7; font-size: 14px; }
.markdown-body :deep(p) { margin: 0 0 8px; }
.markdown-body :deep(p:last-child) { margin-bottom: 0; }
.markdown-body :deep(strong) { font-weight: 600; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 20px; margin: 6px 0; }
.markdown-body :deep(li) { margin: 2px 0; }
.markdown-body :deep(code) { background: #e8e8e8; padding: 1px 5px; border-radius: 3px; font-size: 12px; color: #d63384; font-family: 'SF Mono', 'Fira Code', monospace; }
.bubble.user .markdown-body :deep(code) { background: rgba(255,255,255,0.2); color: #fff; }
.markdown-body :deep(pre) { background: #f6f8fa; border: 1px solid #e8e8e8; border-radius: 8px; padding: 12px 14px; overflow-x: auto; margin: 8px 0; }
.markdown-body :deep(pre code) { background: none; padding: 0; color: #24292e; font-size: 12px; line-height: 1.5; }
.markdown-body :deep(table) { border-collapse: collapse; width: 100%; margin: 8px 0; font-size: 13px; }
.markdown-body :deep(th), .markdown-body :deep(td) { border: 1px solid #e0e0e0; padding: 6px 10px; text-align: left; }
.markdown-body :deep(th) { background: #f5f5f5; font-weight: 600; }
.markdown-body :deep(blockquote) { border-left: 3px solid #4a7fc1; padding: 6px 12px; margin: 8px 0; background: #f8f9fb; color: #595959; font-size: 13px; }
.markdown-body :deep(h1), .markdown-body :deep(h2), .markdown-body :deep(h3) { margin: 12px 0 6px; font-weight: 600; }
.markdown-body :deep(h1) { font-size: 18px; }
.markdown-body :deep(h2) { font-size: 16px; }
.markdown-body :deep(h3) { font-size: 14px; }
.markdown-body :deep(a) { color: #4a7fc1; text-decoration: none; }
.markdown-body :deep(a:hover) { text-decoration: underline; }
.markdown-body :deep(.hljs) { background: #f6f8fa; padding: 0; }

/* 输入区域 */
.chat-input-area { padding: 12px 24px 16px; border-top: 1px solid #e5e7eb; flex-shrink: 0; background: #fff; }
.input-wrapper { max-width: 860px; margin: 0 auto; }
.chat-input-card { position: relative; background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.05), 0 1px 3px rgba(0,0,0,0.03); padding: 6px 8px 6px 12px; transition: box-shadow 0.2s, border-color 0.2s; display: flex; align-items: flex-end; gap: 8px; }
.chat-input-card:focus-within { border-color: #4a7fc1; box-shadow: 0 4px 14px rgba(74,127,193,0.12), 0 2px 5px rgba(0,0,0,0.05); }
.chat-input-card :deep(.chat-input) { background: transparent; border: none; box-shadow: none; resize: none; padding: 4px 0; font-size: 14px; flex: 1; }
.chat-input-card :deep(.chat-input:focus) { border: none; box-shadow: none; }
.chat-input-card :deep(.chat-input textarea) { font-size: 14px; line-height: 1.6; }
.send-btn { flex-shrink: 0; margin-bottom: 4px; }
.input-hint { font-size: 11px; color: rgba(0,0,0,0.35); margin-top: 6px; text-align: right; max-width: 860px; margin-left: auto; margin-right: auto; }
</style>