<template>
  <div class="dashboard-page">
    <!-- 顶部标题栏 -->
    <div class="dashboard-header">
      <div class="header-left">
        <h2 class="page-title">系统数据看板</h2>
        <p class="page-desc">聚合 AI 交互与 HR 全业务运营数据，实时洞察系统运行状况</p>
      </div>
      <div class="header-right">
        <div class="range-tabs">
          <a-button
            v-for="r in ranges"
            :key="r.key"
            size="small"
            :type="range === r.key ? 'primary' : 'default'"
            class="range-btn"
            @click="changeRange(r.key)"
          >{{ r.label }}</a-button>
        </div>
      </div>
    </div>

    <!-- 第一行：指标卡片 -->
    <a-row :gutter="16" class="metrics-row">
      <!-- AI 交互指标 -->
      <a-col :xl="12" :lg="12" :md="24">
        <a-card class="metric-card" :bordered="false">
          <div class="card-title">AI 交互统计</div>
          <a-row :gutter="8">
            <a-col :span="4" v-for="stat in aiStats" :key="stat.key">
              <div class="metric-item">
                <div class="metric-value" :style="{ color: stat.color }">{{ stat.value }}</div>
                <div class="metric-label">{{ stat.label }}</div>
              </div>
            </a-col>
          </a-row>
        </a-card>
      </a-col>
      <!-- 业务指标 -->
      <a-col :xl="12" :lg="12" :md="24">
        <a-card class="metric-card" :bordered="false">
          <div class="card-title">业务概览</div>
          <a-row :gutter="8">
            <a-col :span="4" v-for="stat in businessStats" :key="stat.key">
              <div class="metric-item">
                <div class="metric-value" :style="{ color: stat.color }">{{ formatNum(stat.value) }}</div>
                <div class="metric-label">{{ stat.label }}</div>
              </div>
            </a-col>
          </a-row>
        </a-card>
      </a-col>
    </a-row>

    <!-- 第二行：趋势图表 -->
    <a-row :gutter="16" class="chart-row">
      <!-- AI 交互趋势 -->
      <a-col :xl="12" :lg="12" :md="24">
        <a-card class="chart-card" :bordered="false">
          <template #title>AI 交互每日趋势</template>
          <div class="chart-container" ref="chartAi"></div>
        </a-card>
      </a-col>
      <!-- 业务操作趋势 -->
      <a-col :xl="12" :lg="12" :md="24">
        <a-card class="chart-card" :bordered="false">
          <template #title>业务操作每日增量</template>
          <div class="chart-container" ref="chartBiz"></div>
        </a-card>
      </a-col>
    </a-row>

    <!-- 第三行：分布统计 -->
    <a-row :gutter="16" class="dist-row">
      <!-- 工具调用分布 -->
      <a-col :xl="8" :lg="8" :md="24">
        <a-card class="chart-card" :bordered="false">
          <template #title>工具调用次数 Top 10</template>
          <div class="chart-container-sm" ref="chartTool"></div>
        </a-card>
      </a-col>
      <!-- 流程类型分布 -->
      <a-col :xl="8" :lg="8" :md="24">
        <a-card class="chart-card" :bordered="false">
          <template #title>流程审批类型分布</template>
          <div class="chart-container-sm" ref="chartFlow"></div>
        </a-card>
      </a-col>
      <!-- 招聘漏斗 -->
      <a-col :xl="8" :lg="8" :md="24">
        <a-card class="chart-card" :bordered="false">
          <template #title>招聘转化漏斗</template>
          <div class="chart-container-sm" ref="chartFunnel"></div>
        </a-card>
      </a-col>
    </a-row>

    <!-- 第四行：最近活动日志 -->
    <a-row :gutter="16" class="activity-row">
      <a-col :span="24">
        <a-card class="chart-card" :bordered="false">
          <template #title>最近活动日志</template>
          <div class="activity-table-wrap">
            <a-table
              :dataSource="activities"
              :columns="activityColumns"
              :pagination="false"
              size="small"
              :loading="activityLoading"
              rowKey="index"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'type'">
                  <a-tag :color="record.type === 'ai' ? 'blue' : 'cyan'" style="font-size:11px">
                    {{ record.type === 'ai' ? 'AI 交互' : '流程审批' }}
                  </a-tag>
                </template>
                <template v-if="column.key === 'summary'">
                  <span class="log-summary">{{ record.summary || '-' }}</span>
                </template>
                <template v-if="column.key === 'time'">
                  <span class="log-time">{{ record.time }}</span>
                </template>
              </template>
            </a-table>
          </div>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { getSummary, getDaily, getToolDistribution } from '@/api/modules/aiDashboard'
import {
  getSystemSummary, getAiDaily, getBusinessDaily,
  getToolDistribution as getToolDist, getFlowDistribution, getRecruitmentFunnel,
  getRecentActivity, type SystemSummary, type ActivityItem
} from '@/api/modules/systemDashboard'

const ranges = [
  { key: 7, label: '近 7 天' },
  { key: 30, label: '近 30 天' },
  { key: 90, label: '近 90 天' }
]
const range = ref(7)

// 图表引用
const chartAi = ref<HTMLElement>()
const chartBiz = ref<HTMLElement>()
const chartTool = ref<HTMLElement>()
const chartFlow = ref<HTMLElement>()
const chartFunnel = ref<HTMLElement>()
let charts: echarts.ECharts[] = []

// 数据
const aiSummary = ref<{
  totalCount: number
  totalToolCalls: number
  totalTokens: number
  avgDurationMs: number
  errorCount: number
}>({
  totalCount: 0, totalToolCalls: 0, totalTokens: 0, avgDurationMs: 0, errorCount: 0
})
const sysSummary = ref<SystemSummary>({
  employeeCount: 0, resumeCount: 0, flowCount: 0,
  jobPostCount: 0, performanceCount: 0, trainingCount: 0
})

// 最近活动日志
const activities = ref<ActivityItem[]>([])
const activityLoading = ref(false)
const activityColumns = [
  { title: '类型', key: 'type', width: 100 },
  { title: '操作', key: 'action', width: 120 },
  { title: '内容摘要', key: 'summary' },
  { title: '详情/状态', key: 'detail', width: 120 },
  { title: '时间', key: 'time', width: 140 }
]

// 颜色配置 - 遵循 Ant Design 色板
const PRIMARY = '#1890ff'
const COLORS = {
  blue: ['#69c0ff', PRIMARY],
  green: ['#95de64', '#52c41a'],
  orange: ['#ffc53d', '#fa8c16'],
  cyan: ['#5cdbd3', '#13c2c2'],
  purple: ['#b37feb', '#722ed1'],
  red: ['#ff7875', '#f5222d']
}

const aiStats = computed(() => [
  { key: 'total', label: '对话总量', value: aiSummary.value.totalCount.toLocaleString(), color: COLORS.blue[1] },
  { key: 'tools', label: '工具调用', value: aiSummary.value.totalToolCalls.toLocaleString(), color: COLORS.green[1] },
  { key: 'tokens', label: 'Token 消耗', value: (aiSummary.value.totalTokens / 1000).toFixed(1) + 'k', color: COLORS.orange[1] },
  { key: 'duration', label: '平均耗时', value: aiSummary.value.avgDurationMs + 'ms', color: COLORS.purple[1] },
  { key: 'errors', label: '错误次数', value: aiSummary.value.errorCount, color: COLORS.red[1] }
])

const businessStats = computed(() => [
  { key: 'employee', label: '员工总数', value: sysSummary.value.employeeCount, color: COLORS.blue[1] },
  { key: 'resume', label: '简历总量', value: sysSummary.value.resumeCount, color: COLORS.green[1] },
  { key: 'flow', label: '流程总数', value: sysSummary.value.flowCount, color: COLORS.orange[1] },
  { key: 'job', label: '岗位数量', value: sysSummary.value.jobPostCount, color: COLORS.cyan[1] },
  { key: 'perf', label: '绩效记录', value: sysSummary.value.performanceCount, color: '#eb2f96' },
  { key: 'train', label: '培训课程', value: sysSummary.value.trainingCount, color: COLORS.orange[1] }
])

// 工具方法
function formatNum(n: number): string {
  if (n >= 1000) {
    return (n / 1000).toFixed(1) + 'k'
  }
  return n.toLocaleString()
}

function disposeCharts() {
  charts.forEach(c => c && c.dispose())
  charts = []
}

// 渐变生成 - 细柱子精致风格
function getGradient(colors: string[]): echarts.Gradient {
  return {
    type: 'linear',
    x: 0, y: 0, x2: 0, y2: 1,
    colorStops: [
      { offset: 0, color: colors[0] },
      { offset: 1, color: colors[1] }
    ]
  }
}

// 通用横向柱状图配置（符合 Ant Design 规范：细柱子、圆角、虚线网格）
function getCommonHorizontalBarConfig(): any {
  return {
    grid: {
      left: 70,
      right: 30,
      top: 10,
      bottom: 8,
      containLabel: true
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: '#e8e8e8',
      textStyle: { color: 'rgba(0, 0, 0, 0.85)', fontSize: 12 },
      axisPointer: {
        type: 'shadow',
        shadowStyle: { color: 'rgba(24, 144, 255, 0.1)' }
      }
    },
    xAxis: {
      type: 'value',
      splitLine: {
        lineStyle: { type: 'dashed', color: '#e8e8e8', width: 1 }
      },
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: 'rgba(0, 0, 0, 0.45)', fontSize: 11 }
    },
    yAxis: {
      type: 'category',
      splitLine: { show: false },
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: 'rgba(0, 0, 0, 0.65)', fontSize: 12 }
    }
  }
}

// AI 交互趋势 - Token 消耗柱状图（改进：细柱子，精致风格）
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
      trigger: 'axis',
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: '#e8e8e8',
      textStyle: { color: 'rgba(0, 0, 0, 0.85)' }
    },
    legend: {
      top: 0,
      right: 10,
      itemWidth: 12,
      itemHeight: 8,
      textStyle: { color: 'rgba(0, 0, 0, 0.65)', fontSize: 12 }
    },
    grid: {
      left: 50,
      right: 55,
      top: 35,
      bottom: 25,
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: '#e8e8e8' } },
      axisTick: { show: false },
      axisLabel: { color: 'rgba(0, 0, 0, 0.45)', fontSize: 11 }
    },
    yAxis: [
      {
        type: 'value',
        name: '对话',
        nameTextStyle: { fontSize: 12, color: 'rgba(0, 0, 0, 0.45)' },
        splitLine: {
          lineStyle: { type: 'dashed', color: '#e8e8e8' }
        },
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: 'rgba(0, 0, 0, 0.45)', fontSize: 11 }
      },
      {
        type: 'value',
        name: 'Token',
        nameTextStyle: { fontSize: 12, color: 'rgba(0, 0, 0, 0.45)' },
        splitLine: { show: false },
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: {
          color: 'rgba(0, 0, 0, 0.45)',
          fontSize: 11,
          formatter: (v: number) => v >= 1000 ? (v / 1000) + 'k' : v
        }
      }
    ],
    series: [
      {
        name: '对话量',
        type: 'bar',
        barWidth: '30%',
        barMinHeight: 2,
        barMaxWidth: 30,
        yAxisIndex: 0,
        itemStyle: {
          color: getGradient(COLORS.blue),
          borderRadius: [4, 4, 0, 0]
        },
        emphasis: {
          itemStyle: { shadowBlur: 6, shadowColor: 'rgba(24, 144, 255, 0.3)' }
        },
        data: chats
      },
      {
        name: 'Token 消耗',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        symbol: 'circle',
        symbolSize: 4,
        lineStyle: { color: COLORS.orange[1], width: 2 },
        itemStyle: { color: COLORS.orange[1] },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(250, 140, 22, 0.2)' },
              { offset: 1, color: 'rgba(250, 140, 22, 0)' }
            ]
          }
        },
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
      trigger: 'axis',
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: '#e8e8e8',
      textStyle: { color: 'rgba(0, 0, 0, 0.85)' }
    },
    legend: {
      top: 0,
      left: 10,
      itemWidth: 12,
      itemHeight: 8,
      textStyle: { color: 'rgba(0, 0, 0, 0.65)', fontSize: 12 }
    },
    grid: {
      left: 45,
      right: 20,
      top: 35,
      bottom: 25,
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: '#e8e8e8' } },
      axisTick: { show: false },
      axisLabel: { color: 'rgba(0, 0, 0, 0.45)', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { type: 'dashed', color: '#e8e8e8' } },
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: 'rgba(0, 0, 0, 0.45)', fontSize: 11 }
    },
    series: [
      {
        name: '简历新增',
        type: 'bar',
        barWidth: '30%',
        barMinHeight: 2,
        barMaxWidth: 28,
        itemStyle: {
          color: getGradient(COLORS.green),
          borderRadius: [4, 4, 0, 0]
        },
        emphasis: {
          itemStyle: { shadowBlur: 6, shadowColor: 'rgba(82, 196, 26, 0.3)' }
        },
        data: resume
      },
      {
        name: '流程发起',
        type: 'bar',
        barWidth: '30%',
        barMinHeight: 2,
        barMaxWidth: 28,
        itemStyle: {
          color: getGradient(COLORS.orange),
          borderRadius: [4, 4, 0, 0]
        },
        emphasis: {
          itemStyle: { shadowBlur: 6, shadowColor: 'rgba(250, 140, 22, 0.3)' }
        },
        data: flow
      }
    ]
  })
}

async function renderToolDistribution() {
  if (!chartTool.value) return
  const myChart = echarts.init(chartTool.value)
  charts.push(myChart)
  const list = await getToolDist(range.value)
  const names = (list || []).map(i => i.name)
  const values = (list || []).map(i => i.value)

  const option = getCommonHorizontalBarConfig()
  option.series = [{
    type: 'bar',
    data: values.reverse(),
    barWidth: '50%',
    itemStyle: {
      color: getGradient(COLORS.blue),
      borderRadius: [0, 4, 4, 0]
    },
    label: {
      show: true,
      position: 'right',
      fontSize: 11,
      color: 'rgba(0, 0, 0, 0.65)'
    },
    emphasis: {
      itemStyle: { shadowBlur: 6, shadowColor: 'rgba(24, 144, 255, 0.3)' }
    }
  }]
  option.yAxis.data = names.reverse()
  myChart.setOption(option)
}

const flowTypeMap: Record<string, string> = {
  'ONBOARD': '入职',
  'LEAVE': '离职',
  'TRANSFER': '调岗',
  'REGULAR': '转正',
  'PERFORMANCE': '绩效'
}

async function renderFlowDistribution() {
  if (!chartFlow.value) return
  const myChart = echarts.init(chartFlow.value)
  charts.push(myChart)
  const list = await getFlowDistribution(range.value)
  const names = (list || []).map(i => flowTypeMap[i.name] || i.name)
  const values = (list || []).map(i => i.value)

  const option = getCommonHorizontalBarConfig()
  option.series = [{
    type: 'bar',
    data: values.reverse(),
    barWidth: '50%',
    itemStyle: {
      color: getGradient(COLORS.cyan),
      borderRadius: [0, 4, 4, 0]
    },
    label: {
      show: true,
      position: 'right',
      fontSize: 11,
      color: 'rgba(0, 0, 0, 0.65)'
    },
    emphasis: {
      itemStyle: { shadowBlur: 6, shadowColor: 'rgba(19, 194, 194, 0.3)' }
    }
  }]
  option.yAxis.data = names.reverse()
  myChart.setOption(option)
}

const resumeStatusLabels = ['待筛选', '面试中', '录用', '归档']

async function renderRecruitmentFunnel() {
  if (!chartFunnel.value) return
  const myChart = echarts.init(chartFunnel.value)
  charts.push(myChart)
  const list = await getRecruitmentFunnel()
  const labels = (list || []).map(d => resumeStatusLabels[d.status_key] || `状态${d.status_key}`)
  const values = (list || []).map(d => d.value)

  const option = getCommonHorizontalBarConfig()
  option.series = [{
    type: 'bar',
    data: values.reverse(),
    barWidth: '50%',
    itemStyle: {
      color: getGradient(COLORS.orange),
      borderRadius: [0, 4, 4, 0]
    },
    label: {
      show: true,
      position: 'right',
      fontSize: 11,
      color: 'rgba(0, 0, 0, 0.65)'
    },
    emphasis: {
      itemStyle: { shadowBlur: 6, shadowColor: 'rgba(250, 140, 22, 0.3)' }
    }
  }]
  option.yAxis.data = labels.reverse()
  myChart.setOption(option)
}

function handleResize() {
  charts.forEach(c => c && c.resize())
}

async function loadAll() {
  disposeCharts()
  activityLoading.value = true
  const [aiSum, sysSum] = await Promise.all([
    getSummary(range.value),
    getSystemSummary(range.value)
  ])
  aiSummary.value = aiSum
  sysSummary.value = sysSum
  await Promise.all([
    renderAiTrend(),
    renderBusinessTrend(),
    renderToolDistribution(),
    renderFlowDistribution(),
    renderRecruitmentFunnel()
  ])
  // 加载最近活动日志
  try {
    activities.value = await getRecentActivity(20)
  } finally {
    activityLoading.value = false
  }
}

function changeRange(key: number) {
  range.value = key
  loadAll()
}

onMounted(() => {
  loadAll()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  disposeCharts()
})
</script>

<style scoped>
.dashboard-page {
  padding: 16px 24px 24px;
  min-height: calc(100vh - 56px);
  background: #f0f2f5;
  box-sizing: border-box;
}

/* 头部 */
.dashboard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.85);
}
.page-desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: rgba(0, 0, 0, 0.45);
}
.range-tabs {
  display: inline-flex;
  gap: 4px;
  background: #f0f2f5;
  padding: 4px;
  border-radius: 6px;
}
.range-btn {
  border: none;
  border-radius: 4px;
  font-size: 13px;
  padding: 4px 16px;
}

/* 指标行 */
.metrics-row {
  margin-bottom: 16px;
}
.metric-card {
  background: #ffffff;
  border-radius: 4px;
  box-shadow: 0 1px 2px rgba(0, 21, 41, 0.08);
}
.card-title {
  font-size: 16px;
  font-weight: 500;
  color: rgba(0, 0, 0, 0.85);
  margin-bottom: 16px;
}
.metric-item {
  text-align: center;
  padding: 12px 4px;
  background: #fafafa;
  border-radius: 4px;
}
.metric-value {
  font-size: 20px;
  font-weight: 600;
  line-height: 1.2;
  margin-bottom: 6px;
}
.metric-label {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
  line-height: 1.2;
}

/* 图表行 */
.chart-row {
  margin-bottom: 16px;
}
.chart-card {
  background: #ffffff;
  border-radius: 4px;
  box-shadow: 0 1px 2px rgba(0, 21, 41, 0.08);
}
.chart-container {
  height: 320px;
  padding: 4px 0;
}
.chart-container-sm {
  height: 250px;
  padding: 4px 0;
}
.dist-row {
}

/* 活动日志 */
.activity-row {
  margin-top: 16px;
}
.activity-table-wrap {
  padding: 0;
}
.activity-table-wrap :deep(.ant-table) {
  font-size: 12px;
}
.activity-table-wrap :deep(.ant-table-thead > tr > th) {
  background: #fafafa;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.65);
}
.log-summary {
  max-width: 400px;
  display: inline-block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: rgba(0, 0, 0, 0.85);
}
.log-time {
  color: rgba(0, 0, 0, 0.45);
  font-size: 11px;
}

/* 响应式 */
@media (max-width: 1200px) {
  .metric-card a-col {
    span: 8;
  }
}
</style>
