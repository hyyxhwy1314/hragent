<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { Row, Col, Card, Statistic, Input, Button } from 'ant-design-vue'
import {
  UserOutlined, TeamOutlined, FileTextOutlined, TrophyOutlined,
  ArrowUpOutlined, ArrowDownOutlined, RobotOutlined, SendOutlined
} from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import { h } from 'vue'
import { employeeApi } from '@/api/modules/employee'
import { jobPostApi } from '@/api/modules/jobPost'
import { resumeApi } from '@/api/modules/resume'
import { performanceApi } from '@/api/modules/performance'
import { getSummary, getDaily, getToolDistribution } from '@/api/modules/aiDashboard'
import {
  getSystemSummary, getAiDaily, getBusinessDaily,
  getRecentActivity, type SystemSummary, type ActivityItem
} from '@/api/modules/systemDashboard'

const router = useRouter()
const currentUser = ref(JSON.parse(localStorage.getItem('hragent_user') || '{}'))
const quickAsk = ref('')

const todayText = computed(() => {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 星期${week}`
})

// ========== 业务统计 ==========
const stats = ref({ employees: 0, jobPosts: 0, resumes: 0, performances: 0 })
const cards = [
  { title: '在职员工', value: 'employees', icon: UserOutlined, trend: '+2.3%', up: true, color: '#4a7fc1' },
  { title: '开放岗位', value: 'jobPosts', icon: TeamOutlined, trend: '+5.1%', up: true, color: '#13C2C2' },
  { title: '简历池', value: 'resumes', icon: FileTextOutlined, trend: '-1.2%', up: false, color: '#FA8C16' },
  { title: '绩效记录', value: 'performances', icon: TrophyOutlined, trend: '+8.7%', up: true, color: '#722ED1' }
]

// ========== 数据看板 ==========
const ranges = [
  { key: 7, label: '近 7 天' },
  { key: 30, label: '近 30 天' },
  { key: 90, label: '近 90 天' }
]
const range = ref(7)

const chartAi = ref<HTMLElement>()
const chartBiz = ref<HTMLElement>()
const chartToken = ref<HTMLElement>()
let charts: echarts.ECharts[] = []

const aiSummary = ref({ totalCount: 0, totalToolCalls: 0, totalTokens: 0, avgDurationMs: 0, errorCount: 0 })
const sysSummary = ref<SystemSummary>({ employeeCount: 0, resumeCount: 0, flowCount: 0, jobPostCount: 0, performanceCount: 0, trainingCount: 0 })

// 最近活动日志
const activities = ref<ActivityItem[]>([])
const activityLoading = ref(false)
const activityColumns = [
  { title: '类型', key: 'type', width: 100 },
  { title: '操作', key: 'action', width: 100 },
  { title: '内容摘要', key: 'summary' },
  { title: 'Token / 申请人', key: 'detail2', width: 130 },
  { title: '状态', key: 'detail', width: 80 },
  { title: '时间', key: 'time', width: 140 }
]

const COLORS = {
  blue: ['#69c0ff', '#1890ff'],
  green: ['#95de64', '#52c41a'],
  orange: ['#ffc53d', '#fa8c16'],
  cyan: ['#5cdbd3', '#13c2c2'],
  purple: ['#b37feb', '#722ed1'],
  red: ['#ff7875', '#f5222d']
}

function getGradient(colors: string[]): echarts.Gradient {
  return {
    type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
    colorStops: [
      { offset: 0, color: colors[0] },
      { offset: 1, color: colors[1] }
    ]
  }
}

function getCommonHorizontalBarConfig(): any {
  return {
    grid: { left: 70, right: 30, top: 10, bottom: 8, containLabel: true },
    tooltip: {
      trigger: 'axis', backgroundColor: 'rgba(255,255,255,0.95)', borderColor: '#e8e8e8',
      textStyle: { color: 'rgba(0,0,0,0.85)', fontSize: 12 },
      axisPointer: { type: 'shadow', shadowStyle: { color: 'rgba(24,144,255,0.1)' } }
    },
    xAxis: {
      type: 'value',
      splitLine: { lineStyle: { type: 'dashed', color: '#e8e8e8', width: 1 } },
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: 'rgba(0,0,0,0.45)', fontSize: 11 }
    },
    yAxis: {
      type: 'category', splitLine: { show: false },
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: 'rgba(0,0,0,0.65)', fontSize: 12 }
    }
  }
}

function disposeCharts() {
  charts.forEach(c => c && c.dispose())
  charts = []
}

async function renderAiTrend() {
  if (!chartAi.value) return
  const myChart = echarts.init(chartAi.value)
  charts.push(myChart)
  const daily = await getAiDaily(range.value)
  const dates = daily.map(d => d.dateKey)
  const chats = daily.map(d => d.totalCount)
  const tokens = daily.map(d => (d.totalInputTokens + d.totalOutputTokens))
  myChart.setOption({
    tooltip: {
      trigger: 'axis', backgroundColor: 'rgba(255,255,255,0.95)', borderColor: '#e8e8e8',
      textStyle: { color: 'rgba(0,0,0,0.85)' }
    },
    legend: { top: 0, right: 10, itemWidth: 12, itemHeight: 8, textStyle: { color: 'rgba(0,0,0,0.65)', fontSize: 12 } },
    grid: { left: 50, right: 55, top: 35, bottom: 25, containLabel: true },
    xAxis: {
      type: 'category', data: dates,
      axisLine: { lineStyle: { color: '#e8e8e8' } }, axisTick: { show: false },
      axisLabel: { color: 'rgba(0,0,0,0.45)', fontSize: 11 }
    },
    yAxis: [
      {
        type: 'value', name: '对话', nameTextStyle: { fontSize: 12, color: 'rgba(0,0,0,0.45)' },
        splitLine: { lineStyle: { type: 'dashed', color: '#e8e8e8' } },
        axisLine: { show: false }, axisTick: { show: false },
        axisLabel: { color: 'rgba(0,0,0,0.45)', fontSize: 11 }
      },
      {
        type: 'value', name: 'Token', nameTextStyle: { fontSize: 12, color: 'rgba(0,0,0,0.45)' },
        splitLine: { show: false }, axisLine: { show: false }, axisTick: { show: false },
        axisLabel: { color: 'rgba(0,0,0,0.45)', fontSize: 11, formatter: (v: number) => v >= 1000 ? (v / 1000) + 'k' : v }
      }
    ],
    series: [
      {
        name: '对话量', type: 'bar', barWidth: '30%', barMinHeight: 2, barMaxWidth: 30, yAxisIndex: 0,
        itemStyle: { color: getGradient(COLORS.blue), borderRadius: [4, 4, 0, 0] },
        emphasis: { itemStyle: { shadowBlur: 6, shadowColor: 'rgba(24,144,255,0.3)' } },
        data: chats
      },
      {
        name: 'Token 消耗', type: 'line', yAxisIndex: 1, smooth: true, symbol: 'circle', symbolSize: 4,
        lineStyle: { color: COLORS.orange[1], width: 2 }, itemStyle: { color: COLORS.orange[1] },
        areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(250,140,22,0.2)' }, { offset: 1, color: 'rgba(250,140,22,0)' }] } },
        data: tokens
      }
    ]
  })
}

async function renderBusinessTrend() {
  if (!chartBiz.value) return
  const myChart = echarts.init(chartBiz.value)
  charts.push(myChart)
  const daily = await getBusinessDaily(range.value)
  const dates = daily.map(d => d.dateKey)
  const flow = daily.map(d => d.flowCount)
  const resume = daily.map(d => d.resumeCount)
  myChart.setOption({
    tooltip: {
      trigger: 'axis', backgroundColor: 'rgba(255,255,255,0.95)', borderColor: '#e8e8e8',
      textStyle: { color: 'rgba(0,0,0,0.85)' }
    },
    legend: { top: 0, left: 10, itemWidth: 12, itemHeight: 8, textStyle: { color: 'rgba(0,0,0,0.65)', fontSize: 12 } },
    grid: { left: 45, right: 20, top: 35, bottom: 25, containLabel: true },
    xAxis: {
      type: 'category', data: dates,
      axisLine: { lineStyle: { color: '#e8e8e8' } }, axisTick: { show: false },
      axisLabel: { color: 'rgba(0,0,0,0.45)', fontSize: 11 }
    },
    yAxis: {
      type: 'value', splitLine: { lineStyle: { type: 'dashed', color: '#e8e8e8' } },
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: 'rgba(0,0,0,0.45)', fontSize: 11 }
    },
    series: [
      {
        name: '简历新增', type: 'bar', barWidth: '30%', barMinHeight: 2, barMaxWidth: 28,
        itemStyle: { color: getGradient(COLORS.green), borderRadius: [4, 4, 0, 0] },
        emphasis: { itemStyle: { shadowBlur: 6, shadowColor: 'rgba(82,196,26,0.3)' } },
        data: resume
      },
      {
        name: '流程发起', type: 'bar', barWidth: '30%', barMinHeight: 2, barMaxWidth: 28,
        itemStyle: { color: getGradient(COLORS.orange), borderRadius: [4, 4, 0, 0] },
        emphasis: { itemStyle: { shadowBlur: 6, shadowColor: 'rgba(250,140,22,0.3)' } },
        data: flow
      }
    ]
  })
}

async function renderTokenStat() {
  if (!chartToken.value) return
  const myChart = echarts.init(chartToken.value)
  charts.push(myChart)
  const daily = await getAiDaily(range.value)
  const dates = daily.map(d => d.dateKey)
  const tokens = daily.map(d => (d.totalInputTokens + d.totalOutputTokens))
  myChart.setOption({
    tooltip: {
      trigger: 'axis', backgroundColor: 'rgba(255,255,255,0.95)', borderColor: '#e8e8e8',
      textStyle: { color: 'rgba(0,0,0,0.85)' }
    },
    grid: { left: 50, right: 20, top: 10, bottom: 25, containLabel: true },
    xAxis: {
      type: 'category', data: dates,
      axisLine: { lineStyle: { color: '#e8e8e8' } }, axisTick: { show: false },
      axisLabel: { color: 'rgba(0,0,0,0.45)', fontSize: 11 }
    },
    yAxis: {
      type: 'value', name: 'Token', nameTextStyle: { fontSize: 12, color: 'rgba(0,0,0,0.45)' },
      splitLine: { lineStyle: { type: 'dashed', color: '#e8e8e8' } },
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: 'rgba(0,0,0,0.45)', fontSize: 11, formatter: (v: number) => v >= 1000 ? (v / 1000) + 'k' : v }
    },
    series: [{
      name: 'Token 消耗', type: 'bar', barWidth: '40%', barMinHeight: 2, barMaxWidth: 36,
      itemStyle: { color: getGradient(COLORS.orange), borderRadius: [4, 4, 0, 0] },
      emphasis: { itemStyle: { shadowBlur: 6, shadowColor: 'rgba(250,140,22,0.3)' } },
      data: tokens
    }]
  })
}

function handleResize() {
  charts.forEach(c => c && c.resize())
}

async function loadDashboard() {
  disposeCharts()
  activityLoading.value = true
  const [aiSum, sysSum] = await Promise.all([
    getSummary(range.value),
    getSystemSummary(range.value)
  ])
  aiSummary.value = aiSum
  sysSummary.value = sysSum
  await Promise.all([
    renderAiTrend(), renderBusinessTrend(),
    renderTokenStat()
  ])
  try { activities.value = await getRecentActivity(20) } finally { activityLoading.value = false }
}

function changeRange(key: number) { range.value = key; loadDashboard() }

// 快捷提问
const quickPrompts = ['查询员工张三的信息', '发起一名员工的入职流程', '本月有哪些绩效结果']
function askAgent(text?: string) {
  const q = (text ?? quickAsk.value).trim()
  router.push({ path: '/agent', query: q ? { q } : {} })
  quickAsk.value = ''
}

const hoverBg = ref<Record<string, string | undefined>>({})

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
  <div class="dashboard-page">
    <!-- ===== 问候区 ===== -->
    <Card :bordered="false" class="greet-card">
      <div class="greet-inner">
        <div class="greet-left">
          <div class="greet-title-row">
            <h2 class="greet-title">你好，{{ currentUser.empName || 'HR' }}，欢迎回到 HR-Agent</h2>
            <span class="greet-date">{{ todayText }}</span>
          </div>
          <p class="greet-sub">管理员工、岗位、简历与绩效数据，或直接向 AI 助手提问。</p>
          <div class="greet-ask">
            <Input v-model:value="quickAsk" placeholder="问问 AI 助手：例如「查询员工张三的信息」" @pressEnter="askAgent()" />
            <Button type="primary" :icon="h(SendOutlined)" @click="askAgent()">提问</Button>
          </div>
          <div class="greet-prompts">
            <span v-for="p in quickPrompts" :key="p" class="prompt-chip" @click="askAgent(p)">{{ p }}</span>
          </div>
        </div>
        <div class="greet-right">
          <RobotOutlined class="greet-bot" />
        </div>
      </div>
    </Card>

    <!-- ===== 快速统计卡片 ===== -->
    <Row :gutter="[16, 16]">
      <Col v-for="c in cards" :key="c.title" :xs="24" :sm="12" :lg="6">
        <Card :bordered="false" hoverable class="stat-card">
          <div style="display:flex;justify-content:space-between;align-items:flex-start">
            <div>
              <div style="color:rgba(0,0,0,0.45);font-size:13px;margin-bottom:8px">{{ c.title }}</div>
              <Statistic :value="(stats as any)[c.value]" :value-style="{ fontWeight: 600 }" />
              <div style="margin-top:8px;font-size:12px">
                <span :style="{ color: c.up ? '#52C41A' : '#FF4D4F' }">
                  <component :is="c.up ? ArrowUpOutlined : ArrowDownOutlined" /> {{ c.trend }}
                </span>
                <span style="color:rgba(0,0,0,0.45);margin-left:6px">较上月</span>
              </div>
            </div>
            <div :style="{ width:44,height:44,borderRadius:10,background:`linear-gradient(135deg,${c.color}15,${c.color}35)`,display:'flex',alignItems:'center',justifyContent:'center',color:c.color,fontSize:22 }">
              <component :is="c.icon" />
            </div>
          </div>
        </Card>
      </Col>
    </Row>

    <!-- ===== 快捷入口 ===== -->
    <Row :gutter="[16, 16]" style="margin-top:16px">
      <Col :xs="24" :lg="24">
        <Card :bordered="false" title="快捷入口">
          <div style="display:flex;flex-wrap:wrap;gap:12px">
            <router-link v-for="item in [
              { path:'/employees',label:'员工管理',color:'#4a7fc1' },
              { path:'/job-posts',label:'岗位管理',color:'#13C2C2' },
              { path:'/resumes',label:'简历管理',color:'#FA8C16' },
              { path:'/performances',label:'绩效管理',color:'#722ED1' },
              { path:'/training-courses',label:'培训课程',color:'#EB2F96' },
              { path:'/ability-tags',label:'能力标签',color:'#52C41A' },
              { path:'/todo',label:'我的待办',color:'#1890ff' },
              { path:'/agent',label:'AI 助手',color:'#4a7fc1' }
            ]" :key="item.path" :to="item.path" custom v-slot="{ navigate }">
              <a @click="navigate" :style="{
                flex:'1 1 22%',minWidth:'150px',padding:'16px',borderRadius:'10px',
                background:hoverBg?.[item.path] || '#fafafa',textDecoration:'none',color:'inherit',
                transition:'all 0.2s',cursor:'pointer'
              }" @mouseenter="() => { const m = {}; m[item.path] = item.color+'15'; Object.assign(hoverBg.value, m) }"
                @mouseleave="() => { const m = {}; m[item.path] = undefined; Object.assign(hoverBg.value, m) }">
                <div style="font-weight:500">{{ item.label }}</div>
                <div style="font-size:12px;color:rgba(0,0,0,0.45);margin-top:4px">进入模块 →</div>
              </a>
            </router-link>
          </div>
        </Card>
      </Col>
    </Row>

    <!-- ===== 数据看板 ===== -->
    <div class="dash-section">
      <div class="dash-section-header">
        <h3 class="dash-section-title">系统数据看板</h3>
        <div class="range-tabs">
          <a-button v-for="r in ranges" :key="r.key" size="small"
            :type="range === r.key ? 'primary' : 'default'" class="range-btn"
            @click="changeRange(r.key)">{{ r.label }}</a-button>
        </div>
      </div>

      <!-- 概览指标 -->
      <Row :gutter="[16, 16]" class="dash-metrics">
        <Col :xl="12" :lg="12" :md="24">
          <Card :bordered="false" class="dash-metric-card">
            <div class="metric-card-title">AI 交互统计</div>
            <Row :gutter="8">
              <Col :span="4" v-for="s in [
                { label:'对话总量',value:aiSummary.totalCount.toLocaleString(),color:COLORS.blue[1] },
                { label:'工具调用',value:aiSummary.totalToolCalls.toLocaleString(),color:COLORS.green[1] },
                { label:'Token',value:(aiSummary.totalTokens/1000).toFixed(1)+'k',color:COLORS.orange[1] },
                { label:'平均耗时',value:aiSummary.avgDurationMs+'ms',color:COLORS.purple[1] },
                { label:'错误次数',value:aiSummary.errorCount,color:COLORS.red[1] }
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
          <Card :bordered="false" class="dash-metric-card">
            <div class="metric-card-title">业务概览</div>
            <Row :gutter="8">
              <Col :span="4" v-for="s in [
                { label:'员工总数',value:sysSummary.employeeCount,color:COLORS.blue[1] },
                { label:'简历总量',value:sysSummary.resumeCount,color:COLORS.green[1] },
                { label:'流程总数',value:sysSummary.flowCount,color:COLORS.orange[1] },
                { label:'岗位数量',value:sysSummary.jobPostCount,color:COLORS.cyan[1] },
                { label:'绩效记录',value:sysSummary.performanceCount,color:'#eb2f96' },
                { label:'培训课程',value:sysSummary.trainingCount,color:COLORS.orange[1] }
              ]" :key="s.label">
                <div class="metric-item">
                  <div class="metric-val" :style="{ color: s.color }">{{ s.value >= 1000 ? (s.value/1000).toFixed(1)+'k' : s.value }}</div>
                  <div class="metric-lbl">{{ s.label }}</div>
                </div>
              </Col>
            </Row>
          </Card>
        </Col>
      </Row>

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
            <template #title>业务操作每日增量</template>
            <div class="chart-container" ref="chartBiz"></div>
          </Card>
        </Col>
      </Row>

      <!-- 分布图表 - Token 消耗统计 -->
      <Row :gutter="[16, 16]" class="dash-charts">
        <Col :span="24">
          <Card :bordered="false" class="chart-card">
            <template #title>Token 消耗统计</template>
            <div class="chart-container" ref="chartToken"></div>
          </Card>
        </Col>
      </Row>

      <!-- 最近活动日志 -->
      <Row :gutter="[16, 16]" class="dash-charts">
        <Col :span="24">
          <Card :bordered="false" class="chart-card">
            <template #title>最近活动日志</template>
            <div class="activity-table-wrap">
              <a-table :dataSource="activities" :columns="activityColumns" :pagination="false" size="small" :loading="activityLoading" rowKey="index">
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'type'">
                    <a-tag :color="record.type === 'ai' ? 'blue' : 'cyan'" style="font-size:11px">{{ record.type === 'ai' ? 'AI 交互' : '流程审批' }}</a-tag>
                  </template>
                  <template v-if="column.key === 'summary'">
                    <span class="log-summary">{{ record.summary || '-' }}</span>
                  </template>
                  <template v-if="column.key === 'detail2'">
                    <span v-if="record.type === 'ai' && record.tokenCount != null" class="log-token">{{ record.tokenCount.toLocaleString() }} Token</span>
                    <span v-else-if="record.type === 'flow' && record.applicant" class="log-applicant">{{ record.applicant }}</span>
                    <span v-else class="log-token">-</span>
                  </template>
                  <template v-if="column.key === 'detail'">
                    <a-tag :color="record.detail === '已通过' ? 'green' : record.detail === '已拒绝' ? 'red' : record.detail === '已取消' ? 'default' : 'orange'" style="font-size:11px">{{ record.detail }}</a-tag>
                  </template>
                  <template v-if="column.key === 'time'">
                    <span class="log-time">{{ record.time }}</span>
                  </template>
                </template>
              </a-table>
            </div>
          </Card>
        </Col>
      </Row>
    </div>
  </div>
</template>

<style scoped>
.dashboard-page { padding: 16px 24px 24px; min-height: calc(100vh - 56px); background: #f0f2f5; }

/* 问候区 */
.greet-card {
  background: linear-gradient(135deg, #eef3f9 0%, #f7fafd 100%);
  border: 1px solid #e8eef5; border-radius: 14px; margin-bottom: 16px; overflow: hidden;
}
.greet-card :deep(.ant-card-body) { padding: 24px; }
.greet-inner { display: flex; align-items: center; justify-content: space-between; gap: 24px; }
.greet-left { flex: 1; min-width: 0; }
.greet-title-row { display: flex; align-items: baseline; gap: 12px; flex-wrap: wrap; }
.greet-title { font-size: 20px; font-weight: 600; color: rgba(0,0,0,0.88); margin: 0; }
.greet-date { font-size: 12px; color: #6b7280; }
.greet-sub { font-size: 13px; color: #6b7280; margin: 6px 0 16px; }
.greet-ask { display: flex; gap: 8px; max-width: 560px; }
.greet-prompts { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
.prompt-chip { font-size: 12px; color: #4a7fc1; background: #fff; border: 1px solid #dbe4ee; padding: 3px 12px; border-radius: 16px; cursor: pointer; transition: all 0.15s; }
.prompt-chip:hover { background: #e8f0fe; border-color: #4a7fc1; }
.greet-right { flex-shrink: 0; width: 88px; height: 88px; border-radius: 50%; background: linear-gradient(135deg,#4a7fc1,#7ba3d0); display: flex; align-items: center; justify-content: center; box-shadow: 0 8px 20px rgba(74,127,193,0.25); }
.greet-bot { font-size: 40px; color: #fff; }
.stat-card { border-radius: 12px; }

/* 数据看板 */
.dash-section { margin-top: 24px; }
.dash-section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.dash-section-title { margin: 0; font-size: 18px; font-weight: 600; color: rgba(0,0,0,0.85); }
.range-tabs { display: inline-flex; gap: 4px; background: #f0f2f5; padding: 4px; border-radius: 6px; }
.range-btn { border: none; border-radius: 4px; font-size: 13px; padding: 4px 16px; }
.dash-metrics { margin-bottom: 16px; }
.dash-metric-card { background: #fff; border-radius: 4px; }
.metric-card-title { font-size: 16px; font-weight: 500; color: rgba(0,0,0,0.85); margin-bottom: 16px; }
.metric-item { text-align: center; padding: 12px 4px; background: #fafafa; border-radius: 4px; }
.metric-val { font-size: 20px; font-weight: 600; line-height: 1.2; margin-bottom: 6px; }
.metric-lbl { font-size: 12px; color: rgba(0,0,0,0.45); }
.dash-charts { margin-bottom: 16px; }
.chart-card { background: #fff; border-radius: 4px; }
.chart-container { height: 320px; padding: 4px 0; }
.activity-table-wrap { padding: 0; }
.activity-table-wrap :deep(.ant-table) { font-size: 12px; }
.activity-table-wrap :deep(.ant-table-thead > tr > th) { background: #fafafa; font-size: 12px; color: rgba(0,0,0,0.65); }
.log-summary { max-width: 400px; display: inline-block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: rgba(0,0,0,0.85); }
.log-token { font-size: 12px; color: #fa8c16; font-weight: 500; }
.log-applicant { font-size: 12px; color: #4a7fc1; }
.log-time { color: rgba(0,0,0,0.45); font-size: 11px; }
</style>