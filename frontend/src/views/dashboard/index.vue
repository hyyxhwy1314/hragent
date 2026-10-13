<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import * as echarts from 'echarts'
import { Row, Col, Card, Statistic, Input, Button } from 'ant-design-vue'
import {
  UserOutlined, TeamOutlined, FileTextOutlined, TrophyOutlined, SendOutlined
} from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import { h } from 'vue'
import { employeeApi } from '@/api/modules/employee'
import { jobPostApi } from '@/api/modules/jobPost'
import { resumeApi } from '@/api/modules/resume'
import { performanceApi } from '@/api/modules/performance'
import { getSummary } from '@/api/modules/aiDashboard'
import {
  getSystemSummary, getAiDaily, getTokenSummary, getTokenTrend,
  getBehaviorModule, getBehaviorDaily, getRecruitmentFunnel, getRecentActivity,
  type ActivityItem
} from '@/api/modules/systemDashboard'

const router = useRouter()
const currentUser = ref(JSON.parse(localStorage.getItem('hragent_user') || '{}'))
const quickAsk = ref('')

const todayText = computed(() => {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 星期${week}`
})

// ========== 业务统计（真实数据，无虚假趋势） ==========
const stats = ref({ employees: 0, jobPosts: 0, resumes: 0, performances: 0 })
const cards = [
  { title: '在职员工', value: 'employees', icon: UserOutlined, color: '#4a7fc1' },
  { title: '开放岗位', value: 'jobPosts', icon: TeamOutlined, color: '#3aa8ab' },
  { title: '简历池', value: 'resumes', icon: FileTextOutlined, color: '#d28b2f' },
  { title: '绩效记录', value: 'performances', icon: TrophyOutlined, color: '#8266c9' }
]

// ========== 数据看板 ==========
const ranges = [
  { key: 7, label: '近 7 天' },
  { key: 30, label: '近 30 天' },
  { key: 90, label: '近 90 天' }
]
const range = ref(7)

const chartToken = ref<HTMLElement>()
const chartTokenModule = ref<HTMLElement>()
const chartAi = ref<HTMLElement>()
const chartModule = ref<HTMLElement>()
const chartBehavior = ref<HTMLElement>()
const chartFunnel = ref<HTMLElement>()
let charts: echarts.ECharts[] = []

const aiSummary = ref({ totalCount: 0, totalToolCalls: 0, totalTokens: 0, avgDurationMs: 0, errorCount: 0 })
const sysSummary = ref({ employeeCount: 0, resumeCount: 0, flowCount: 0, jobPostCount: 0, performanceCount: 0, trainingCount: 0 })
const tokenSummary = ref({ todayTotal: 0, todayInput: 0, todayOutput: 0, periodTotal: 0, avgPerDay: 0, peakDay: '', peakValue: 0 })

// 最近活动日志
const activities = ref<ActivityItem[]>([])
const activityLoading = ref(false)
const activityColumns = [
  { title: '用户', key: 'user', width: 90 },
  { title: '模块', key: 'module', width: 100 },
  { title: '操作', key: 'action', width: 110 },
  { title: '内容摘要', key: 'summary' },
  { title: 'Token', key: 'token', width: 100 },
  { title: '状态', key: 'status', width: 80 },
  { title: '时间', key: 'time', width: 150 }
]

// ========== 图表通用配置 ==========
const AXIS_LABEL = 'rgba(0,0,0,0.45)'
const GRID_DASHED = 'rgba(0,0,0,0.10)'
const BLUE = ['#cddff5', '#4a7fc1']
const GREEN = ['#c6e4d1', '#3f9a44']
const CYAN = ['#c3e2e3', '#2f8f92']
const ORANGE = ['#f0d5b4', '#c98a2e']
const PURPLE = ['#d5cae9', '#6f5cb3']

function getGradient(colors: string[]): echarts.Gradient {
  return {
    type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
    colorStops: [
      { offset: 0, color: colors[0] },
      { offset: 1, color: colors[1] }
    ]
  }
}

function baseTooltip() {
  return {
    trigger: 'axis' as const,
    backgroundColor: 'rgba(255,255,255,0.96)',
    borderColor: 'rgba(0,0,0,0.12)',
    borderWidth: 1,
    padding: [8, 12],
    textStyle: { color: 'rgba(0,0,0,0.85)', fontSize: 12 },
    axisPointer: { type: 'shadow' as const, shadowStyle: { color: 'rgba(74,127,193,0.08)' } }
  }
}

/** 柱状图通用：胶囊圆角柱顶、虚线网格、隐藏轴线 */
function barSeries(name: string, data: number[], colors: string[]) {
  return {
    name,
    type: 'bar' as const,
    barWidth: '36%',
    barMaxWidth: 26,
    barMinHeight: 2,
    data,
    itemStyle: { color: getGradient(colors), borderRadius: [6, 6, 0, 0] },
    emphasis: { itemStyle: { shadowBlur: 6, shadowColor: 'rgba(0,0,0,0.12)' } }
  }
}

function categoryAxis(dates: string[]) {
  return {
    type: 'category' as const,
    data: dates,
    axisLine: { lineStyle: { color: 'rgba(0,0,0,0.15)' } },
    axisTick: { show: false },
    axisLabel: { color: AXIS_LABEL, fontSize: 11 }
  }
}

function valueAxis(extra?: Record<string, unknown>) {
  return {
    type: 'value' as const,
    splitLine: { lineStyle: { type: 'dashed' as const, color: GRID_DASHED } },
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: AXIS_LABEL, fontSize: 11 },
    minInterval: 1,
    ...extra
  }
}

/** 横向条形图通用配置（虚线网格 + 智能 tooltip + 隐藏轴线） */
function horizontalBarOption(names: string[], values: number[], colors: string[]) {
  return {
    grid: { left: 12, right: 42, top: 8, bottom: 8, containLabel: true },
    tooltip: {
      trigger: 'axis' as const,
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: 'rgba(0,0,0,0.12)',
      borderWidth: 1,
      padding: [8, 12],
      textStyle: { color: 'rgba(0,0,0,0.85)', fontSize: 12 },
      axisPointer: { type: 'shadow' as const, shadowStyle: { color: 'rgba(74,127,193,0.08)' } }
    },
    xAxis: {
      type: 'value' as const,
      splitLine: { lineStyle: { type: 'dashed' as const, color: GRID_DASHED } },
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: AXIS_LABEL, fontSize: 11 },
      minInterval: 1
    },
    yAxis: {
      type: 'category' as const,
      data: names,
      splitLine: { show: false },
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: 'rgba(0,0,0,0.65)', fontSize: 12 }
    },
    series: [
      {
        type: 'bar' as const,
        data: values,
        barWidth: '52%',
        itemStyle: { color: getGradient(colors), borderRadius: [0, 6, 6, 0] },
        label: { show: true, position: 'right' as const, fontSize: 11, color: 'rgba(0,0,0,0.65)', formatter: (p: any) => p.value >= 10000 ? (p.value / 10000).toFixed(1) + '万' : p.value },
        emphasis: { itemStyle: { shadowBlur: 6, shadowColor: 'rgba(0,0,0,0.12)' } }
      }
    ]
  }
}

function disposeCharts() {
  charts.forEach(c => c && c.dispose())
  charts = []
}

function fmtShort(v: number) {
  if (v >= 10000) return (v / 10000).toFixed(1) + '万'
  if (v >= 1000) return (v / 1000).toFixed(1) + 'k'
  return v.toLocaleString()
}

function fmtFull(v: number) {
  return v.toLocaleString()
}

// ========== Token 消耗看板 ==========
/** 每日 Token 消耗总趋势（单系列柱状，避免输入/输出堆叠混乱） */
async function renderTokenTrend() {
  if (!chartToken.value) return
  const myChart = echarts.init(chartToken.value)
  charts.push(myChart)
  const daily = await getTokenTrend(range.value)
  myChart.setOption({
    tooltip: baseTooltip(),
    grid: { left: 12, right: 12, top: 20, bottom: 8, containLabel: true },
    xAxis: categoryAxis(daily.map(d => d.dateKey)),
    yAxis: valueAxis({ axisLabel: { color: AXIS_LABEL, fontSize: 11, formatter: (v: number) => v >= 10000 ? (v / 10000).toFixed(1) + '万' : v } }),
    series: [barSeries('Token 消耗', daily.map(d => d.inputTokens + d.outputTokens), BLUE)]
  })
}

/** 各模块 Token 消耗 Top5（横向条形，展示真实数值而非百分比） */
async function renderTokenModule() {
  if (!chartTokenModule.value) return
  const myChart = echarts.init(chartTokenModule.value)
  charts.push(myChart)
  const list = await getBehaviorModule(range.value)
  const top = (list || [])
    .filter(i => i.tokenCount > 0)
    .sort((a, b) => b.tokenCount - a.tokenCount)
    .slice(0, 5)
  const names = top.map(i => i.name).reverse()
  const values = top.map(i => i.tokenCount).reverse()
  myChart.setOption(horizontalBarOption(names, values, BLUE))
}

// ========== AI 交互每日趋势 ==========
async function renderAiTrend() {
  if (!chartAi.value) return
  const myChart = echarts.init(chartAi.value)
  charts.push(myChart)
  const daily = await getAiDaily(range.value)
  const dates = daily.map(d => d.dateKey)
  const chats = daily.map(d => d.totalCount)
  const tokens = daily.map(d => d.totalInputTokens + d.totalOutputTokens)
  myChart.setOption({
    tooltip: baseTooltip(),
    legend: {
      top: 0, right: 8,
      itemWidth: 12, itemHeight: 8,
      textStyle: { color: 'rgba(0,0,0,0.65)', fontSize: 12 }
    },
    grid: { left: 12, right: 52, top: 32, bottom: 8, containLabel: true },
    xAxis: categoryAxis(dates),
    yAxis: [
      valueAxis(),
      {
        type: 'value' as const,
        splitLine: { show: false },
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: AXIS_LABEL, fontSize: 11, formatter: (v: number) => v >= 10000 ? (v / 10000).toFixed(1) + '万' : v }
      }
    ],
    series: [
      {
        name: '对话量', type: 'bar' as const, barWidth: '36%', barMaxWidth: 24, barMinHeight: 2, yAxisIndex: 0,
        itemStyle: { color: getGradient(BLUE), borderRadius: [6, 6, 0, 0] },
        emphasis: { itemStyle: { shadowBlur: 6, shadowColor: 'rgba(0,0,0,0.12)' } },
        data: chats
      },
      {
        name: 'Token 消耗', type: 'line' as const, yAxisIndex: 1, smooth: true, symbol: 'circle', symbolSize: 5,
        lineStyle: { color: ORANGE[1], width: 2 },
        itemStyle: { color: ORANGE[1] },
        areaStyle: { color: getGradient(['rgba(210,139,47,0.18)', 'rgba(210,139,47,0)']) },
        data: tokens
      }
    ]
  })
}

// ========== 各模块操作量 Top10 ==========
async function renderModuleTop() {
  if (!chartModule.value) return
  const myChart = echarts.init(chartModule.value)
  charts.push(myChart)
  const list = await getBehaviorModule(range.value)
  const top = (list || []).slice(0, 10)
  const names = top.map(i => i.name).reverse()
  const values = top.map(i => i.value).reverse()
  myChart.setOption(horizontalBarOption(names, values, CYAN))
}

// ========== 操作行为趋势（按天） ==========
async function renderBehaviorDaily() {
  if (!chartBehavior.value) return
  const myChart = echarts.init(chartBehavior.value)
  charts.push(myChart)
  const daily = await getBehaviorDaily(range.value)
  myChart.setOption({
    tooltip: baseTooltip(),
    grid: { left: 12, right: 12, top: 20, bottom: 8, containLabel: true },
    xAxis: categoryAxis(daily.map(d => (d as any).dateKey || d.name)),
    yAxis: valueAxis(),
    series: [barSeries('操作量', daily.map(d => d.value), GREEN)]
  })
}

// ========== 招聘转化漏斗 ==========
const resumeStatusLabels: Record<number, string> = { 0: '待筛选', 1: '已通过', 2: '已淘汰', 3: '面试中', 4: '归档' }
async function renderFunnel() {
  if (!chartFunnel.value) return
  const myChart = echarts.init(chartFunnel.value)
  charts.push(myChart)
  const list = await getRecruitmentFunnel()
  const data = (list || [])
    .map(i => ({ name: resumeStatusLabels[i.status_key] || `状态${i.status_key}`, value: i.value }))
    .filter(i => i.value > 0)
  myChart.setOption({
    tooltip: {
      trigger: 'item' as const,
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: 'rgba(0,0,0,0.12)',
      borderWidth: 1,
      padding: [8, 12],
      textStyle: { color: 'rgba(0,0,0,0.85)', fontSize: 12 },
      formatter: (p: any) => `${p.name}：${p.value} 人`
    },
    legend: { top: 0, left: 8, itemWidth: 12, itemHeight: 8, textStyle: { color: 'rgba(0,0,0,0.65)', fontSize: 12 } },
    grid: { top: 40 },
    series: [
      {
        type: 'funnel' as const,
        top: 44, bottom: 8, left: 10, right: 60,
        minSize: '18%',
        sort: 'ascending' as const,
        gap: 4,
        label: { show: true, position: 'right' as const, color: 'rgba(0,0,0,0.65)', fontSize: 12, formatter: (p: any) => `${p.name} ${p.value}人` },
        labelLine: { show: false },
        itemStyle: {
          borderColor: '#fff',
          borderWidth: 2,
          color: (p: any) => {
            const colors = [BLUE, CYAN, GREEN, PURPLE, ORANGE]
            return getGradient(colors[p.dataIndex % colors.length])
          }
        },
        data
      }
    ]
  })
}

function handleResize() {
  charts.forEach(c => c && c.resize())
}

async function loadDashboard() {
  disposeCharts()
  activityLoading.value = true
  const [aiSum, sysSum, tSum] = await Promise.all([
    getSummary(range.value),
    getSystemSummary(range.value),
    getTokenSummary(range.value)
  ])
  aiSummary.value = aiSum
  sysSummary.value = sysSum
  tokenSummary.value = tSum
  try {
    await Promise.all([
      renderTokenTrend(), renderTokenModule(),
      renderAiTrend(), renderModuleTop(),
      renderBehaviorDaily(), renderFunnel()
    ])
    activities.value = await getRecentActivity(20)
  } finally {
    activityLoading.value = false
  }
}

function changeRange(key: number) {
  range.value = key
  loadDashboard()
}

// 快捷提问
const quickPrompts = ['查询员工张三的信息', '发起一名员工的入职流程', '本月有哪些绩效结果']
function askAgent(text?: string) {
  const q = (text ?? quickAsk.value).trim()
  router.push({ path: '/agent', query: q ? { q } : {} })
  quickAsk.value = ''
}

// 模块徽章配色
function moduleColor(m?: string): { bg: string; color: string } {
  const map: Record<string, [string, string]> = {
    'AI交互': ['#eaf2fb', '#4a7fc1'],
    '简历分析': ['#edf7ed', '#3f9a44'],
    '流程审批': ['#fdf6e8', '#c77d2a'],
    '员工管理': ['#e8f6f6', '#2f8f92'],
    '员工自助': ['#f3f0fb', '#6b5cb3'],
    '岗位管理': ['#f3f0fb', '#6b5cb3'],
    '绩效管理': ['#fdeef4', '#c2457c'],
    '培训管理': ['#eaf2fb', '#4a7fc1'],
    '合同审阅': ['#edf7ed', '#3f9a44'],
    '文件管理': ['#f2f2f2', '#8c8c8c']
  }
  const c = map[m || ''] || ['#eef2f8', '#5a6b7f']
  return { bg: c[0], color: c[1] }
}

onMounted(async () => {
  try {
    const [emp, job, rsm, perf] = await Promise.all([
      employeeApi.page({ pageNum: 1, pageSize: 1 }),
      jobPostApi.page({ pageNum: 1, pageSize: 1 }),
      resumeApi.page({ pageNum: 1, pageSize: 1 }),
      performanceApi.page({ pageNum: 1, pageSize: 1 })
    ])
    stats.value = { employees: emp.total, jobPosts: job.total, resumes: rsm.total, performances: perf.total }
  } catch { /* ignore */ }
  loadDashboard()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  disposeCharts()
})
</script>

<template>
  <div class="page-container">
    <!-- ===== 欢迎条（简洁，无装饰） ===== -->
    <div class="welcome-bar">
      <div>
        <h2 class="welcome-title">你好，{{ currentUser.empName || 'HR' }}，欢迎回到 HR-Agent</h2>
        <p class="welcome-sub">管理员工、岗位、简历与绩效数据，或直接向 AI 助手提问。<span class="welcome-date">{{ todayText }}</span></p>
      </div>
      <div class="welcome-ask">
        <Input v-model:value="quickAsk" placeholder="问问 AI 助手：例如「查询员工张三的信息」" @pressEnter="askAgent()" />
        <Button type="primary" :icon="h(SendOutlined)" @click="askAgent()">提问</Button>
      </div>
    </div>

    <!-- ===== 快速统计卡片（真实数据） ===== -->
    <Row :gutter="[16, 16]" class="stat-row">
      <Col v-for="c in cards" :key="c.title" :xs="24" :sm="12" :lg="6">
        <Card :bordered="false" class="stat-card">
          <div class="stat-inner">
            <div class="stat-icon" :style="{ background: `linear-gradient(135deg,${c.color}14,${c.color}30)`, color: c.color }">
              <component :is="c.icon" />
            </div>
            <div class="stat-info">
              <div class="stat-label">{{ c.title }}</div>
              <Statistic :value="(stats as any)[c.value]" :value-style="{ fontWeight: 600, fontSize: 22 }" />
            </div>
          </div>
        </Card>
      </Col>
    </Row>

    <!-- ===== 快捷入口 ===== -->
    <Row :gutter="[16, 16]" class="stat-row">
      <Col :span="24">
        <Card :bordered="false" class="quick-card">
          <div class="quick-title">快捷入口</div>
          <div class="quick-list">
            <router-link v-for="item in [
              { path:'/employees',label:'员工管理',color:'#4a7fc1' },
              { path:'/job-posts',label:'岗位管理',color:'#3aa8ab' },
              { path:'/resumes',label:'简历管理',color:'#d28b2f' },
              { path:'/performances',label:'绩效管理',color:'#8266c9' },
              { path:'/training-courses',label:'培训课程',color:'#3f9a44' },
              { path:'/ability-tags',label:'能力标签',color:'#6b5cb3' },
              { path:'/todo',label:'我的待办',color:'#4a7fc1' },
              { path:'/agent',label:'AI 助手',color:'#2f8f92' }
            ]" :key="item.path" :to="item.path" class="quick-item">
              <span class="quick-dot" :style="{ background: item.color }"></span>
              <span class="quick-label">{{ item.label }}</span>
              <span class="quick-arrow">→</span>
            </router-link>
          </div>
        </Card>
      </Col>
    </Row>

    <!-- ===== 系统数据看板 ===== -->
    <div class="dash-section">
      <div class="dash-section-header">
        <div>
          <h3 class="dash-section-title">系统数据看板</h3>
          <p class="dash-section-sub">AI 交互、业务操作与 Token 消耗的实时洞察</p>
        </div>
        <div class="range-tabs">
          <button
            v-for="r in ranges" :key="r.key"
            class="range-btn" :class="{ active: range === r.key }"
            @click="changeRange(r.key)"
          >{{ r.label }}</button>
        </div>
      </div>

      <!-- 概览指标 -->
      <Row :gutter="[16, 16]" class="dash-metrics">
        <Col :xl="12" :lg="12" :md="24">
          <Card :bordered="false" class="metric-card">
            <div class="metric-card-title">AI 交互统计</div>
            <Row :gutter="8">
              <Col :span="4" v-for="s in [
                { label:'对话总量',value:aiSummary.totalCount.toLocaleString(),color:'#4a7fc1' },
                { label:'工具调用',value:aiSummary.totalToolCalls.toLocaleString(),color:'#3f9a44' },
                { label:'Token 消耗',value:fmtShort(aiSummary.totalTokens),color:'#d28b2f' },
                { label:'平均耗时',value:aiSummary.avgDurationMs + 'ms',color:'#8266c9' },
                { label:'错误次数',value:aiSummary.errorCount,color:'#c2457c' }
              ]" :key="s.label">
                <div class="metric-item">
                  <div class="metric-val" :style="{ color: s.color }">{{ s.value }}</div>
                  <div class="metric-lbl">{{ s.label }}</div>
                </div>
              </Col>
            </Row>
          </Card>
        </Col>
        <Col :xl="12" :lg="12" :md="24">
          <Card :bordered="false" class="metric-card">
            <div class="metric-card-title">业务概览</div>
            <Row :gutter="8">
              <Col :span="4" v-for="s in [
                { label:'员工总数',value:sysSummary.employeeCount,color:'#4a7fc1' },
                { label:'简历总量',value:sysSummary.resumeCount,color:'#3f9a44' },
                { label:'流程总数',value:sysSummary.flowCount,color:'#d28b2f' },
                { label:'岗位数量',value:sysSummary.jobPostCount,color:'#2f8f92' },
                { label:'绩效记录',value:sysSummary.performanceCount,color:'#8266c9' },
                { label:'培训课程',value:sysSummary.trainingCount,color:'#6b5cb3' }
              ]" :key="s.label">
                <div class="metric-item">
                  <div class="metric-val" :style="{ color: s.color }">{{ s.value >= 10000 ? fmtShort(s.value) : s.value.toLocaleString() }}</div>
                  <div class="metric-lbl">{{ s.label }}</div>
                </div>
              </Col>
            </Row>
          </Card>
        </Col>
      </Row>

      <!-- Token 消耗看板（重点） -->
      <Card :bordered="false" class="chart-card token-card">
        <template #title>
          <div class="card-head">
            <span class="card-title-inline">Token 消耗看板</span>
            <span class="card-sub">基于 AI 交互日志实时统计</span>
          </div>
        </template>
        <div class="token-metrics">
          <div class="token-metric">
            <div class="token-value" style="color:#4a7fc1">{{ fmtFull(tokenSummary.todayTotal) }}</div>
            <div class="token-label">今日消耗</div>
          </div>
          <div class="token-metric">
            <div class="token-value" style="color:#4a7fc1">{{ fmtFull(tokenSummary.todayInput) }}</div>
            <div class="token-label">今日输入</div>
          </div>
          <div class="token-metric">
            <div class="token-value" style="color:#2f8f92">{{ fmtFull(tokenSummary.todayOutput) }}</div>
            <div class="token-label">今日输出</div>
          </div>
          <div class="token-metric">
            <div class="token-value">{{ fmtFull(tokenSummary.periodTotal) }}</div>
            <div class="token-label">周期累计</div>
          </div>
          <div class="token-metric">
            <div class="token-value">{{ fmtFull(tokenSummary.avgPerDay) }}</div>
            <div class="token-label">日均消耗</div>
          </div>
          <div class="token-metric">
            <div class="token-value" style="color:#d28b2f">{{ fmtFull(tokenSummary.peakValue) }}</div>
            <div class="token-label">峰值日 {{ tokenSummary.peakDay || '-' }}</div>
          </div>
        </div>
        <div class="token-charts">
          <div class="token-trend-wrap">
            <div class="mini-title">每日 Token 消耗趋势</div>
            <div class="token-trend" ref="chartToken"></div>
          </div>
          <div class="token-module-wrap">
            <div class="mini-title">各模块 Token 消耗 Top 5</div>
            <div class="token-module" ref="chartTokenModule"></div>
          </div>
        </div>
      </Card>

      <!-- 趋势图表 -->
      <Row :gutter="[16, 16]" class="dash-charts">
        <Col :xl="12" :lg="12" :md="24">
          <Card :bordered="false" class="chart-card">
            <template #title>AI 交互每日趋势</template>
            <div class="chart-container" ref="chartAi"></div>
          </Card>
        </Col>
        <Col :xl="12" :lg="12" :md="24">
          <Card :bordered="false" class="chart-card">
            <template #title>各模块操作量 Top 10</template>
            <div class="chart-container-sm" ref="chartModule"></div>
          </Card>
        </Col>
      </Row>

      <!-- 行为趋势 + 招聘漏斗 -->
      <Row :gutter="[16, 16]" class="dash-charts">
        <Col :xl="12" :lg="12" :md="24">
          <Card :bordered="false" class="chart-card">
            <template #title>操作行为趋势</template>
            <div class="chart-container" ref="chartBehavior"></div>
          </Card>
        </Col>
        <Col :xl="12" :lg="12" :md="24">
          <Card :bordered="false" class="chart-card">
            <template #title>招聘转化漏斗</template>
            <div class="chart-container-sm" ref="chartFunnel"></div>
          </Card>
        </Col>
      </Row>

      <!-- 最近活动日志 -->
      <Card :bordered="false" class="chart-card">
        <template #title>
          <div class="card-head">
            <span class="card-title-inline">最近活动日志</span>
            <span class="card-sub">覆盖所有操作 / 流程审批 / AI 交互</span>
          </div>
        </template>
        <div class="activity-table-wrap">
          <a-table :dataSource="activities" :columns="activityColumns" :pagination="false" size="middle" :loading="activityLoading" rowKey="index">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'user'">
                <span class="log-user">{{ record.username || '-' }}</span>
              </template>
              <template v-if="column.key === 'module'">
                <span class="module-badge" :style="{ background: moduleColor(record.module).bg, color: moduleColor(record.module).color }">
                  {{ record.module || '-' }}
                </span>
              </template>
              <template v-if="column.key === 'action'">
                <span class="action-name">{{ record.action || '-' }}</span>
              </template>
              <template v-if="column.key === 'summary'">
                <span class="log-summary" :title="record.summary">{{ record.summary || '-' }}</span>
              </template>
              <template v-if="column.key === 'token'">
                <span v-if="Number(record.tokenCount) > 0" class="token-chip">{{ Number(record.tokenCount).toLocaleString() }}</span>
                <span v-else class="token-empty">-</span>
              </template>
              <template v-if="column.key === 'status'">
                <span class="status-dot" :class="{ fail: record.status === 'fail' }"></span>
                <span :class="{ 'status-fail': record.status === 'fail' }">{{ record.status === 'fail' ? '失败' : '成功' }}</span>
              </template>
              <template v-if="column.key === 'time'">
                <span class="log-time">{{ record.time }}</span>
              </template>
            </template>
          </a-table>
        </div>
      </Card>
    </div>
  </div>
</template>

<style scoped>
.page-container { min-height: calc(100vh - 56px); }

/* ===== 欢迎条 ===== */
.welcome-bar {
  display: flex; align-items: flex-end; justify-content: space-between; gap: 24px;
  flex-wrap: wrap; padding: 4px 0 20px;
}
.welcome-title { font-size: 22px; font-weight: 600; color: var(--text-primary); margin: 0 0 6px; letter-spacing: 0.2px; }
.welcome-sub { font-size: 13px; color: var(--text-tertiary); margin: 0; }
.welcome-date { margin-left: 12px; color: var(--text-quaternary); }
.welcome-ask { display: flex; gap: 8px; width: 100%; max-width: 560px; }

/* ===== 统计卡片 ===== */
.stat-row { margin-bottom: 16px; }
.stat-card { border: 1px solid var(--border); border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); }
.stat-card:hover { box-shadow: var(--shadow-md); }
.stat-inner { display: flex; align-items: center; gap: 14px; }
.stat-icon {
  width: 46px; height: 46px; border-radius: 12px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center; font-size: 22px;
}
.stat-label { font-size: 13px; color: var(--text-tertiary); margin-bottom: 4px; }

/* ===== 快捷入口 ===== */
.quick-card { border: 1px solid var(--border); border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); }
.quick-title { font-size: 15px; font-weight: 600; color: var(--text-primary); margin-bottom: 14px; }
.quick-list { display: flex; flex-wrap: wrap; gap: 10px; }
.quick-item {
  flex: 1 1 22%; min-width: 150px; display: flex; align-items: center; gap: 10px;
  padding: 14px 16px; border-radius: var(--radius-md); border: 1px solid var(--border-light);
  background: var(--bg-secondary); text-decoration: none; color: inherit; transition: all 0.2s;
}
.quick-item:hover { border-color: var(--accent-border); background: var(--accent-light); box-shadow: var(--shadow-sm); transform: translateY(-1px); }
.quick-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.quick-label { font-size: 13px; font-weight: 500; color: var(--text-primary); }
.quick-arrow { margin-left: auto; font-size: 12px; color: var(--text-quaternary); }

/* ===== 数据看板 ===== */
.dash-section { margin-top: 8px; }
.dash-section-header { display: flex; align-items: flex-end; justify-content: space-between; margin-bottom: 16px; flex-wrap: wrap; gap: 12px; }
.dash-section-title { margin: 0; font-size: 18px; font-weight: 600; color: var(--text-primary); letter-spacing: 0.1px; }
.dash-section-sub { margin: 4px 0 0; font-size: 12px; color: var(--text-tertiary); }
.range-tabs { display: inline-flex; gap: 4px; background: var(--bg-tertiary); padding: 3px; border-radius: 8px; }
.range-btn { border: none; border-radius: 6px; font-size: 13px; padding: 5px 16px; background: transparent; color: var(--text-secondary); cursor: pointer; transition: all 0.15s; }
.range-btn.active { background: var(--bg-secondary); color: var(--accent); font-weight: 500; box-shadow: var(--shadow-sm); }

.dash-metrics { margin-bottom: 16px; }
.metric-card { border: 1px solid var(--border); border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); }
.metric-card-title { font-size: 15px; font-weight: 600; color: var(--text-primary); margin-bottom: 14px; }
.metric-item { text-align: center; padding: 12px 4px; background: var(--bg-tertiary); border-radius: var(--radius-md); }
.metric-val { font-size: 20px; font-weight: 600; line-height: 1.2; margin-bottom: 6px; }
.metric-lbl { font-size: 12px; color: var(--text-tertiary); }

.dash-charts { margin-bottom: 16px; }
.chart-card { border: 1px solid var(--border); border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); }
.chart-container { height: 320px; padding: 4px 0; }
.chart-container-sm { height: 260px; padding: 4px 0; }
.card-head { display: flex; align-items: baseline; gap: 10px; }
.card-title-inline { font-size: 15px; font-weight: 600; color: var(--text-primary); }
.card-sub { font-size: 12px; color: var(--text-tertiary); font-weight: 400; }

/* ===== Token 消耗看板 ===== */
.token-card :deep(.ant-card-body) { padding-top: 8px; }
.token-metrics {
  display: grid; grid-template-columns: repeat(6, 1fr); gap: 12px; margin: 4px 0 16px;
}
.token-metric {
  padding: 14px 12px; background: var(--bg-tertiary); border-radius: var(--radius-md); text-align: center;
}
.token-value { font-size: 22px; font-weight: 600; color: var(--text-primary); line-height: 1.2; margin-bottom: 6px; }
.token-label { font-size: 12px; color: var(--text-tertiary); }
.token-charts { display: flex; gap: 16px; }
.token-trend-wrap { flex: 1 1 66%; min-width: 0; }
.token-module-wrap { flex: 0 0 34%; min-width: 0; }
.mini-title { font-size: 13px; font-weight: 500; color: var(--text-secondary); margin-bottom: 8px; }
.token-trend { height: 300px; }
.token-module { height: 300px; }

/* ===== 最近活动日志 ===== */
.activity-table-wrap :deep(.ant-table) { font-size: 12px; }
.log-summary { max-width: 420px; display: inline-block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--text-primary); }
.log-user { color: var(--text-primary); font-weight: 500; }
.action-name { color: var(--text-secondary); }
.module-badge { display: inline-block; padding: 2px 10px; border-radius: 10px; font-size: 11px; }
.token-chip { display: inline-block; padding: 1px 8px; border-radius: 8px; background: #fdf6e8; color: #c77d2a; font-size: 11px; font-weight: 500; font-family: var(--font-num); }
.token-empty { color: var(--text-quaternary); }
.status-dot { display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: #3f9a44; margin-right: 6px; vertical-align: middle; }
.status-dot.fail { background: #d64545; }
.status-fail { color: #d64545; }
.log-time { color: var(--text-tertiary); font-size: 11px; font-family: var(--font-num); }

@media (max-width: 992px) {
  .token-metrics { grid-template-columns: repeat(3, 1fr); }
  .token-charts { flex-direction: column; }
}
</style>
