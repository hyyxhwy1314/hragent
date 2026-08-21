<script setup lang="ts">
import { ref, h, computed } from 'vue'
import { Table, Button, Space, Select, Tag, Modal, Timeline, message, App as AntApp } from 'ant-design-vue'
import { ReloadOutlined, PlusOutlined, UnorderedListOutlined, CheckCircleFilled, CloseCircleFilled, ClockCircleOutlined } from '@ant-design/icons-vue'
import {
  listInstances, startProcess, cancelFlow, getFlowTrace,
  FLOW_STATUS_MAP, FLOW_TYPE_MAP,
  type FlowInstance, type FlowTraceVO
} from '@/api/modules/flow'
import { employeeApi, type Employee } from '@/api/modules/employee'
import { resumeApi, type Resume } from '@/api/modules/resume'

defineOptions({ name: 'FlowPage' })
const { modal } = AntApp.useApp()

const loading = ref(false)
const dataSource = ref<FlowInstance[]>([])
const queryForm = ref<Record<string, any>>({})

// 员工列表（用于姓名映射 + 部门主管选择）
const employees = ref<Employee[]>([])
const empMap = computed(() => {
  const m: Record<number, Employee> = {}
  employees.value.forEach(e => { if (e.id) m[e.id] = e })
  return m
})
function empName(id?: number) {
  if (!id) return '-'
  const e = empMap.value[id]
  return e ? (e.empName || e.empNo || `#${id}`) : `#${id}`
}

// 简历列表（入职流程的业务对象，状态=录用）
const resumes = ref<Resume[]>([])
const resumeMap = computed(() => {
  const m: Record<number, Resume> = {}
  resumes.value.forEach(r => { if (r.id) m[r.id] = r })
  return m
})
function bizName(row: FlowInstance) {
  if (!row.bizId) return '-'
  // 入职流程 bizId 是简历ID
  if (row.flowType === 'ONBOARD') {
    const r = resumeMap.value[row.bizId]
    return r ? (r.resumeName || `简历#${row.bizId}`) : `简历#${row.bizId}`
  }
  // 其他流程 bizId 是员工ID
  return empName(row.bizId)
}

const flowTypeOpts = [
  { label: '入职', value: 'ONBOARD' },
  { label: '转正', value: 'REGULAR' },
  { label: '调岗', value: 'TRANSFER' },
  { label: '离职', value: 'LEAVE' }
]
const flowStatusOpts = [
  { label: '进行中', value: 1 },
  { label: '已通过', value: 2 },
  { label: '已拒绝', value: 3 },
  { label: '已撤回', value: 4 }
]

// 当前登录人
const currentUser = JSON.parse(localStorage.getItem('hragent_user') || '{}')

async function fetchList() {
  loading.value = true
  try {
    dataSource.value = await listInstances(queryForm.value) || []
  } catch {
    dataSource.value = []
  } finally {
    loading.value = false
  }
}

async function loadEmployees() {
  try { employees.value = await employeeApi.list() || [] } catch { employees.value = [] }
}

async function loadResumes() {
  try {
    const all = await resumeApi.list() || []
    // 候选条件：未归档（resumeStatus !== 4）的简历均可发起入职
    resumes.value = all.filter(r => r.resumeStatus !== 4)
  } catch { resumes.value = [] }
}

Promise.all([fetchList(), loadEmployees(), loadResumes()])

function onSearch() { fetchList() }
function onReset() { queryForm.value = {}; fetchList() }

// 发起流程弹窗
const startVisible = ref(false)
const startForm = ref<{
  processKey: string
  bizId: number | null
  targetLeaderId: number | null
  targetDeptName: string
  targetPosition: string
}>({
  processKey: 'onboard-process',
  bizId: null,
  targetLeaderId: null,
  targetDeptName: '',
  targetPosition: ''
})

// 是否入职流程（bizId 选简历 + 需指定部门主管）
const isOnboard = computed(() => startForm.value.processKey === 'onboard-process')

// 候选简历列表（入职流程）
const resumeOptions = computed(() =>
  resumes.value
    .filter(r => r.id)
    .map(r => ({
      label: `${r.resumeName || '未命名'}（${r.expectPosition || '-'}）`,
      value: r.id!
    }))
)

// 部门主管列表（入职流程需 HR 指定用人部门主管）
const leaderOptions = computed(() =>
  employees.value
    .filter(e => e.id && e.role === 'DEPT_LEADER' && e.empStatus !== 0)
    .map(e => ({
      label: `${e.empName || '未命名'}（${e.deptName || e.empNo || '-'}）`,
      value: e.id!
    }))
)

function openStart() {
  startForm.value = {
    processKey: 'onboard-process',
    bizId: null,
    targetLeaderId: null,
    targetDeptName: '',
    targetPosition: ''
  }
  startVisible.value = true
}

async function handleStart() {
  if (!startForm.value.bizId) {
    message.warning(isOnboard.value ? '请选择候选人简历' : '请选择业务对象')
    return
  }
  if (isOnboard.value && !startForm.value.targetLeaderId) {
    message.warning('请选择用人部门主管')
    return
  }
  // 组装请求：入职流程通过 bizJson 传递部门主管等参数
  const bizJson = isOnboard.value
    ? JSON.stringify({
        targetLeaderId: startForm.value.targetLeaderId,
        targetDeptName: startForm.value.targetDeptName || undefined,
        targetPosition: startForm.value.targetPosition || undefined
      })
    : undefined
  try {
    await startProcess({
      processKey: startForm.value.processKey,
      bizId: startForm.value.bizId,
      bizJson
    })
    message.success('流程已发起')
    startVisible.value = false
    fetchList()
  } catch {}
}

// 撤回
function onCancel(row: FlowInstance) {
  modal.confirm({
    title: '撤回流程',
    content: `确定撤回「${typeText(row.flowType)}」流程吗？业务对象：${bizName(row)}`,
    okText: '撤回', okType: 'danger', cancelText: '取消',
    onOk: async () => {
      await cancelFlow(row.id)
      message.success('已撤回')
      fetchList()
    }
  })
}

// 轨迹弹窗
const traceVisible = ref(false)
const traceData = ref<FlowTraceVO[]>([])
const traceLoading = ref(false)

async function onViewTrace(row: FlowInstance) {
  traceVisible.value = true
  traceLoading.value = true
  try {
    traceData.value = await getFlowTrace(row.id) || []
  } catch {
    traceData.value = []
  } finally {
    traceLoading.value = false
  }
}

function statusText(s?: number) { return FLOW_STATUS_MAP[s || 0]?.label ?? '-' }
function statusColor(s?: number) { return FLOW_STATUS_MAP[s || 0]?.color ?? 'default' }
function typeText(t: string) { return FLOW_TYPE_MAP[t] || t }

const columns: any[] = [
  { title: '流水号', dataIndex: 'flowNo', width: 180 },
  { title: '类型', dataIndex: 'flowType', width: 80,
    customRender: ({ record }: any) => typeText(record.flowType)
  },
  { title: '业务对象', dataIndex: 'bizId', width: 130,
    customRender: ({ record }: any) => bizName(record)
  },
  { title: '申请人', dataIndex: 'applyEmpId', width: 120,
    customRender: ({ record }: any) => empName(record.applyEmpId)
  },
  { title: '创建时间', dataIndex: 'createTime', width: 180 },
  {
    title: '状态', dataIndex: 'flowStatus', width: 100,
    customRender: ({ record }: any) => h(Tag, { color: statusColor(record.flowStatus) }, () => statusText(record.flowStatus))
  },
  {
    title: '操作', key: 'action', width: 200, fixed: 'right',
    customRender: ({ record }: any) => h(Space, {}, () => [
      h(Button, { size: 'small', type: 'link', onClick: () => onViewTrace(record) },
        () => h(UnorderedListOutlined, {}, () => ' 轨迹')),
      record.flowStatus === 1 ? h(Button, {
        size: 'small', type: 'link', danger: true, onClick: () => onCancel(record)
      }, () => '撤回') : null
    ])
  }
]

// 轨迹时间线状态颜色
function traceDotColor(status: string) {
  if (status === '已通过') return 'green'
  if (status === '已拒绝') return 'red'
  if (status === '待处理') return 'blue'
  return 'gray'
}
function traceStatusColor(status: string) {
  if (status === '已通过') return 'green'
  if (status === '已拒绝') return 'red'
  if (status === '待处理') return 'blue'
  return 'default'
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">流程管理</h2>
      <p class="page-subtitle">发起审批流程，查看流程状态与审批轨迹</p>
    </div>

    <div class="search-card">
      <a-form layout="inline" :model="queryForm">
        <a-form-item label="类型">
          <Select v-model:value="queryForm.flowType" placeholder="全部" allow-clear style="width: 120px"
            :options="flowTypeOpts" />
        </a-form-item>
        <a-form-item label="状态">
          <Select v-model:value="queryForm.flowStatus" placeholder="全部" allow-clear style="width: 120px"
            :options="flowStatusOpts" />
        </a-form-item>
        <a-form-item>
          <Space>
            <Button type="primary" :icon="h(ReloadOutlined)" @click="onSearch">查询</Button>
            <Button @click="onReset">重置</Button>
          </Space>
        </a-form-item>
      </a-form>
    </div>

    <div class="table-card">
      <div class="table-toolbar">
        <div class="table-toolbar-left">
          <strong>流程列表</strong>
          <span style="color: rgba(0,0,0,0.45); font-size: 13px">共 {{ dataSource.length }} 条</span>
        </div>
        <div class="table-toolbar-right">
          <Button type="primary" :icon="h(PlusOutlined)" @click="openStart">发起流程</Button>
        </div>
      </div>

      <Table :columns="columns" :data-source="dataSource" :loading="loading"
        :pagination="{ pageSize: 10, showSizeChanger: true }"
        :scroll="{ x: 1000 }" row-key="id" />
    </div>

    <!-- 发起流程弹窗 -->
    <Modal v-model:open="startVisible" title="发起流程" @ok="handleStart" okText="发起" cancelText="取消" width="520px">
      <a-form layout="vertical">
        <a-form-item label="流程类型">
          <Select v-model:value="startForm.processKey" :options="[
            { label: '入职流程', value: 'onboard-process' }
          ]" />
        </a-form-item>

        <!-- 入职流程：选候选人简历 -->
        <template v-if="isOnboard">
          <a-form-item label="候选人简历" required>
            <Select v-model:value="startForm.bizId" :options="resumeOptions"
              placeholder="请选择已录用的候选人" show-search option-filter-prop="label" />
          </a-form-item>
          <a-form-item label="用人部门主管" required>
            <Select v-model:value="startForm.targetLeaderId" :options="leaderOptions"
              placeholder="请选择用人部门主管" show-search option-filter-prop="label" />
          </a-form-item>
          <a-form-item label="目标部门（可选）">
            <a-input v-model:value="startForm.targetDeptName" placeholder="如：研发部" />
          </a-form-item>
          <a-form-item label="目标岗位（可选）">
            <a-input v-model:value="startForm.targetPosition" placeholder="如：Java工程师，不填取简历意向岗位" />
          </a-form-item>
        </template>

        <!-- 其他流程：选员工（预留，当前只有入职流程） -->
        <a-form-item v-else label="选择员工（业务对象）">
          <Select v-model:value="startForm.bizId"
            placeholder="请选择员工" show-search option-filter-prop="label" />
        </a-form-item>
      </a-form>
    </Modal>

    <!-- 审批轨迹弹窗 -->
    <Modal v-model:open="traceVisible" title="审批轨迹" :footer="null" width="680px">
      <div v-if="traceLoading" style="text-align: center; padding: 24px">加载中...</div>
      <Timeline v-else>
        <Timeline.Item v-for="(node, i) in traceData" :key="i"
          :color="traceDotColor(node.status)">
          <!-- 节点名 + 审批结果（醒目） -->
          <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 6px">
            <span style="font-weight: 600; font-size: 15px">{{ node.nodeName }}</span>
            <template v-if="node.approved === true">
              <CheckCircleFilled style="color: #52c41a; font-size: 16px" />
              <span style="color: #52c41a; font-weight: 600; font-size: 14px">同意</span>
            </template>
            <template v-else-if="node.approved === false">
              <CloseCircleFilled style="color: #ff4d4f; font-size: 16px" />
              <span style="color: #ff4d4f; font-weight: 600; font-size: 14px">拒绝</span>
            </template>
            <template v-else-if="node.status === '待处理'">
              <ClockCircleOutlined style="color: #1890ff; font-size: 14px" />
              <span style="color: #1890ff; font-size: 13px">待处理</span>
            </template>
          </div>
          <!-- 审批人 -->
          <p style="color: rgba(0,0,0,0.65); margin: 2px 0; font-size: 13px">
            审批人：{{ node.assigneeName || '-' }}
          </p>
          <!-- 时间 -->
          <p style="color: rgba(0,0,0,0.45); margin: 2px 0; font-size: 13px">
            <span v-if="node.startTime">开始：{{ node.startTime }}</span>
            <span v-if="node.endTime"> · 完成：{{ node.endTime }}</span>
          </p>
          <!-- 审批意见 -->
          <p v-if="node.comment" style="margin: 6px 0 0; font-size: 13px; padding: 8px 12px; background: #f5f5f5; border-radius: 6px; border-left: 3px solid #d9d9d9">
            意见：{{ node.comment }}
          </p>
        </Timeline.Item>
      </Timeline>
    </Modal>
  </div>
</template>
