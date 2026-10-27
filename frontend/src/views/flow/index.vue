<script setup lang="ts">
import { ref, h, computed, watch, reactive } from 'vue'
import { Table, Button, Space, Select, Tag, Modal, Timeline, message, App as AntApp, Badge } from 'ant-design-vue'
import {
  ReloadOutlined, PlusOutlined, UnorderedListOutlined, CheckCircleFilled, CloseCircleFilled,
  ClockCircleOutlined, ApartmentOutlined
} from '@ant-design/icons-vue'
import {
  listInstances, startProcess, cancelFlow, getFlowTrace, listMyCc, markCcRead,
  listDefinitions, FLOW_STATUS_MAP, FLOW_TYPE_MAP,
  type FlowInstance, type FlowTraceVO, type FlowCcVO, type ProcessDefinition
} from '@/api/modules/flow'
import { employeeApi, type Employee } from '@/api/modules/employee'
import { resumeApi, type Resume } from '@/api/modules/resume'
import FlowDiagramModal from './FlowDiagramModal.vue'

defineOptions({ name: 'FlowPage' })
const { modal } = AntApp.useApp()

const activeTab = ref('list')

const loading = ref(false)
const dataSource = ref<FlowInstance[]>([])
const queryForm = ref<Record<string, any>>({})

// 员工/简历列表：仅「发起流程」弹窗的下拉选项需要，改为打开弹窗时懒加载；
// 列表页的申请人姓名/业务对象名称由后端 FlowInstanceVO、FlowCcVO 直接回填，无需前端全量拉取做映射。
const employees = ref<Employee[]>([])
const resumes = ref<Resume[]>([])

// 内置流程 key：筛选中排除，避免与下方中文基础选项重复
const BUILTIN_PROC_KEYS = new Set([
  'onboard-process', 'regular-process', 'transfer-process', 'leave-process',
  'leave-request-process', 'certificate-process', 'multi-sign-process'
])
// 流程类型筛选项：内置中文类型 + 业务人员自建流程（类型码取定义 key 大写，名称取定义 name）
const flowTypeOpts = computed(() => {
  const base = [
    { label: '入职', value: 'ONBOARD' },
    { label: '转正', value: 'REGULAR' },
    { label: '调岗', value: 'TRANSFER' },
    { label: '离职', value: 'LEAVE' },
    { label: '请假', value: 'LEAVE_REQUEST' },
    { label: '证明', value: 'CERTIFICATE' }
  ]
  const seen = new Set(base.map(o => o.value))
  const custom = definitions.value
    .filter(d => d.key && !BUILTIN_PROC_KEYS.has(d.key))
    .map(d => ({ label: d.name || d.key, value: d.key.toUpperCase() }))
  return [...base, ...custom.filter(o => !seen.has(o.value))]
})
const flowStatusOpts = [
  { label: '进行中', value: 1 },
  { label: '已通过', value: 2 },
  { label: '已拒绝', value: 3 },
  { label: '已撤回', value: 4 }
]

// 服务端分页状态
const pagination = reactive({ current: 1, pageSize: 10, total: 0 })

async function fetchList() {
  loading.value = true
  try {
    const res = await listInstances({
      ...queryForm.value,
      pageNum: pagination.current,
      pageSize: pagination.pageSize
    })
    dataSource.value = res?.records || []
    pagination.total = Number(res?.total || 0)
  } catch {
    dataSource.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

function handleTableChange(pg: { current: number; pageSize: number }) {
  pagination.current = pg.current
  pagination.pageSize = pg.pageSize
  fetchList()
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

// 发起弹窗的下拉选项（员工/简历）按需懒加载，避免进入列表页即全量拉取
let startOptionsLoaded = false
async function ensureStartOptions() {
  if (startOptionsLoaded) return
  startOptionsLoaded = true
  await Promise.all([loadEmployees(), loadResumes()])
}

// 初始化加载：仅拉取流程列表（已部署定义在下方声明后单独加载）
fetchList()

function onSearch() { pagination.current = 1; fetchList() }
function onReset() { queryForm.value = {}; pagination.current = 1; fetchList() }

// ============ 抄送我的 ============
const ccLoading = ref(false)
const ccList = ref<FlowCcVO[]>([])

async function fetchCc() {
  ccLoading.value = true
  try {
    ccList.value = await listMyCc() || []
  } catch {
    ccList.value = []
  } finally {
    ccLoading.value = false
  }
}

async function onMarkRead(row: FlowCcVO) {
  if (row.readFlag) return
  try {
    await markCcRead(row.id)
    row.readFlag = true
    message.success('已标记为已读')
  } catch {}
}

// 切到抄送 Tab 时刷新
watch(activeTab, (v) => { if (v === 'cc') fetchCc() })

// ============ 运行态流程图 ============
const diagramOpen = ref(false)
const diagramFlowId = ref<number>()
const diagramTitle = ref('')
function onViewDiagram(row: { id?: number; flowInstanceId?: number; flowType: string }) {
  diagramFlowId.value = row.id ?? row.flowInstanceId
  diagramTitle.value = `${typeText(row.flowType)}流程图`
  diagramOpen.value = true
}

// 发起流程弹窗
const startVisible = ref(false)
const startForm = ref<{
  processKey: string
  bizId: any
  /** 业务对象类型：EMPLOYEE / RESUME / __none__（不关联） */
  bizType?: string
  targetLeaderId: any
  targetDeptName: string
  targetPosition: string
  /** 自定义流程：从已部署定义中选择的 processKey */
  customKey?: string
  /** 自定义流程：业务参数键值对（提交时拼成 bizJson） */
  bizParams: { key: string; value: string }[]
}>({
  processKey: 'onboard-process',
  bizId: undefined,
  bizType: undefined,
  targetLeaderId: undefined,
  targetDeptName: '',
  targetPosition: '',
  customKey: undefined,
  bizParams: []
})

// 是否自定义流程（从已部署定义列表里选，适配业务人员新建的流程）
const isCustom = computed(() => startForm.value.processKey === '__custom__')

// 已部署流程定义（供自定义发起入口选择，覆盖业务人员自建的新流程）
const definitions = ref<ProcessDefinition[]>([])
const defOptions = computed(() =>
  definitions.value.map(d => ({ label: `${d.name}（${d.key} · v${d.version}）`, value: d.key }))
)
async function loadDefinitions() {
  try {
    definitions.value = (await listDefinitions()) || []
  } catch {
    definitions.value = []
  }
}

// 自建流程的类型码 = 流程定义 key 的大写形式，用定义名称回显中文，避免列表出现原始码
const defNameMap = computed(() => {
  const m: Record<string, string> = {}
  definitions.value.forEach(d => { if (d.key && d.name) m[d.key.toUpperCase()] = d.name })
  return m
})

// 已部署定义须在 definitions 声明之后再加载，供自定义发起入口与「类型」中文回显
loadDefinitions()

// 是否入职流程（bizId 选简历 + 需指定部门主管）
const isOnboard = computed(() => startForm.value.processKey === 'onboard-process')
// 是否调岗流程（bizId 选员工 + 可选目标部门/岗位）
const isTransfer = computed(() => startForm.value.processKey === 'transfer-process')
// 是否转正流程（bizId 只能选实习/试用员工）
const isRegular = computed(() => startForm.value.processKey === 'regular-process')
// 是否员工相关流程（离职/转正/调岗，bizId 选员工）
const isBizEmployee = computed(() =>
  ['leave-process', 'regular-process', 'transfer-process'].includes(startForm.value.processKey)
)

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

// 在职员工列表（离职/调岗流程选员工）
const empOptions = computed(() =>
  employees.value
    .filter(e => e.id && e.empStatus !== 0)
    .map(e => ({
      label: `${e.empName || '未命名'}（${e.empNo || '-'}）`,
      value: e.id!
    }))
)

// 实习/试用员工列表（转正流程只能选这批人，empStatus=2）
const regularEmpOptions = computed(() =>
  employees.value
    .filter(e => e.id && e.empStatus === 2)
    .map(e => ({
      label: `${e.empName || '未命名'}（${e.empNo || '-'}）`,
      value: e.id!
    }))
)

// 自定义流程：业务对象类型（决定 bizId 指向哪张业务表）
const bizTypeOpts = [
  { label: '员工（关联员工档案）', value: 'EMPLOYEE' },
  { label: '简历（关联候选人简历）', value: 'RESUME' },
  { label: '不关联业务对象', value: '__none__' }
]
// 自定义流程：按所选业务对象类型展示可选对象
const customBizOptions = computed(() =>
  startForm.value.bizType === 'EMPLOYEE' ? empOptions.value
    : startForm.value.bizType === 'RESUME' ? resumeOptions.value
    : []
)
// 切换业务对象类型时清空已选业务对象
watch(() => startForm.value.bizType, () => { startForm.value.bizId = undefined })

function addBizParam() { startForm.value.bizParams.push({ key: '', value: '' }) }
function removeBizParam(idx: number) { startForm.value.bizParams.splice(idx, 1) }
/** 将键值对参数拼成 bizJson（无有效参数则返回 undefined） */
function buildBizJson(): string | undefined {
  const obj: Record<string, string> = {}
  startForm.value.bizParams.forEach(p => {
    const k = p.key?.trim()
    if (k) obj[k] = p.value ?? ''
  })
  return Object.keys(obj).length ? JSON.stringify(obj) : undefined
}

/** 统一重置发起表单；open 时刷新一次已部署定义列表，供自定义流程选择 */
function openStartForm(processKey: string) {
  startForm.value = {
    processKey,
    bizId: null,
    bizType: undefined,
    targetLeaderId: null,
    targetDeptName: '',
    targetPosition: '',
    customKey: undefined,
    bizParams: []
  }
  loadDefinitions()
  startVisible.value = true
}

/** 统一入口：打开「发起流程」弹窗，流程类型在弹窗内选择（内置/自定义） */
function openStart() { openStartForm('onboard-process') }

async function handleStart() {
  // 自定义流程：从已部署定义列表里选 processKey，适配业务人员新建的流程
  if (isCustom.value) {
    if (!startForm.value.customKey) {
      message.warning('请选择要发起的流程定义')
      return
    }
    if (!startForm.value.bizType) {
      message.warning('请选择业务对象类型')
      return
    }
    // 关联员工/简历时必须选定具体业务对象；不关联时 bizId 留空
    const linkBiz = startForm.value.bizType !== '__none__'
    if (linkBiz && !startForm.value.bizId) {
      message.warning(startForm.value.bizType === 'RESUME' ? '请选择候选人简历' : '请选择员工')
      return
    }
    try {
      await startProcess({
        processKey: startForm.value.customKey,
        bizId: linkBiz ? startForm.value.bizId : undefined,
        bizType: linkBiz ? startForm.value.bizType : undefined,
        bizJson: buildBizJson()
      })
      message.success('流程已发起')
      startVisible.value = false
      fetchList()
    } catch {}
    return
  }
  if (!startForm.value.bizId) {
    message.warning(isOnboard.value ? '请选择候选人简历' : isBizEmployee.value ? '请选择员工' : '请选择业务对象')
    return
  }
  if (isOnboard.value && !startForm.value.targetLeaderId) {
    message.warning('请选择用人部门主管')
    return
  }
  // 组装请求：入职/调岗流程通过 bizJson 传递业务参数
  const bizJson = isOnboard.value
    ? JSON.stringify({
        targetLeaderId: startForm.value.targetLeaderId,
        targetDeptName: startForm.value.targetDeptName || undefined,
        targetPosition: startForm.value.targetPosition || undefined
      })
    : isTransfer.value
      ? JSON.stringify({
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

async function onViewTrace(id?: number) {
  if (!id) return
  traceVisible.value = true
  traceLoading.value = true
  try {
    traceData.value = await getFlowTrace(id) || []
  } catch {
    traceData.value = []
  } finally {
    traceLoading.value = false
  }
}

function statusText(s?: number) { return FLOW_STATUS_MAP[s || 0]?.label ?? '-' }
function statusColor(s?: number) { return FLOW_STATUS_MAP[s || 0]?.color ?? 'default' }
function typeText(t: string) { return FLOW_TYPE_MAP[t] || defNameMap.value[t] || t }
// 业务对象/申请人名称由后端 VO 回填（bizName / applyEmpName），兜底显示原始ID
function bizName(row: any) { return row?.bizName || (row?.bizId != null ? String(row.bizId) : '-') }
function empName(row: any) { return row?.applyEmpName || (row?.applyEmpId != null ? String(row.applyEmpId) : '-') }

const columns: any[] = [
  { title: '流水号', dataIndex: 'flowNo', width: 180 },
  { title: '类型', dataIndex: 'flowType', width: 90,
    customRender: ({ record }: any) => typeText(record.flowType)
  },
  { title: '业务对象', dataIndex: 'bizId', width: 130,
    customRender: ({ record }: any) => bizName(record)
  },
  { title: '申请人', dataIndex: 'applyEmpId', width: 120,
    customRender: ({ record }: any) => empName(record)
  },
  { title: '创建时间', dataIndex: 'createTime', width: 180 },
  {
    title: '状态', dataIndex: 'flowStatus', width: 100,
    customRender: ({ record }: any) => h(Tag, { color: statusColor(record.flowStatus) }, () => statusText(record.flowStatus))
  },
  {
    title: '操作', key: 'action', width: 240, fixed: 'right',
    customRender: ({ record }: any) => h(Space, {}, () => [
      h(Button, { size: 'small', type: 'link', onClick: () => onViewDiagram(record) },
        () => h(ApartmentOutlined, {}, () => ' 流程图')),
      h(Button, { size: 'small', type: 'link', onClick: () => onViewTrace(record.id) },
        () => h(UnorderedListOutlined, {}, () => ' 轨迹')),
      record.flowStatus === 1 ? h(Button, {
        size: 'small', type: 'link', danger: true, onClick: () => onCancel(record)
      }, () => '撤回') : null
    ])
  }
]

// 抄送我的列表
const ccColumns: any[] = [
  { title: '流水号', dataIndex: 'flowNo', width: 180 },
  { title: '类型', dataIndex: 'flowType', width: 90,
    customRender: ({ record }: any) => typeText(record.flowType)
  },
  { title: '业务对象', dataIndex: 'bizId', width: 130,
    customRender: ({ record }: any) => bizName(record)
  },
  { title: '申请人', dataIndex: 'applyEmpId', width: 110,
    customRender: ({ record }: any) => empName(record)
  },
  { title: '抄送节点', dataIndex: 'nodeName', width: 120 },
  { title: '抄送时间', dataIndex: 'createTime', width: 180 },
  {
    title: '状态', dataIndex: 'flowStatus', width: 100,
    customRender: ({ record }: any) => h(Tag, { color: statusColor(record.flowStatus) }, () => statusText(record.flowStatus))
  },
  {
    title: '操作', key: 'action', width: 200, fixed: 'right',
    customRender: ({ record }: any) => h(Space, {}, () => [
      h(Button, { size: 'small', type: 'link', onClick: () => onViewDiagram(record) },
        () => h(ApartmentOutlined, {}, () => ' 流程图')),
      h(Button, { size: 'small', type: 'link', onClick: () => onViewTrace(record.flowInstanceId) },
        () => h(UnorderedListOutlined, {}, () => ' 轨迹')),
      !record.readFlag ? h(Button, {
        size: 'small', type: 'link', onClick: () => onMarkRead(record)
      }, () => '标记已读') : h(Tag, { color: 'default' }, () => '已读')
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
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">流程管理</h2>
      <p class="page-subtitle">发起审批流程，查看流程状态、审批轨迹与运行态流程图</p>
    </div>

    <a-tabs v-model:activeKey="activeTab">
      <a-tab-pane key="list" tab="流程列表">
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
            :scroll="{ x: 1100 }" row-key="id" />
        </div>
      </a-tab-pane>

      <a-tab-pane key="cc" tab="抄送我的">
        <div class="table-card">
          <div class="table-toolbar">
            <div class="table-toolbar-left">
              <strong>抄送我的流程</strong>
              <span style="color: rgba(0,0,0,0.45); font-size: 13px">共 {{ ccList.length }} 条</span>
            </div>
            <div class="table-toolbar-right">
              <Button :icon="h(ReloadOutlined)" @click="fetchCc">刷新</Button>
            </div>
          </div>
          <Table :columns="ccColumns" :data-source="ccList" :loading="ccLoading"
            :pagination="{ pageSize: 10, showSizeChanger: true }"
            :scroll="{ x: 1100 }" row-key="id">
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'nodeName'">
                <Badge :status="record.readFlag ? 'default' : 'processing'" />
                {{ record.nodeName || '-' }}
              </template>
            </template>
          </Table>
        </div>
      </a-tab-pane>
    </a-tabs>

    <!-- 发起流程弹窗 -->
    <Modal v-model:open="startVisible" title="发起流程" @ok="handleStart" okText="发起" cancelText="取消" width="520px">
      <a-form layout="vertical">
        <a-form-item label="流程类型">
          <Select v-model:value="startForm.processKey" :options="[
            { label: '入职流程', value: 'onboard-process' },
            { label: '转正流程', value: 'regular-process' },
            { label: '调岗流程', value: 'transfer-process' },
            { label: '离职流程', value: 'leave-process' },
            { label: '自定义流程（选择已部署定义）', value: '__custom__' }
          ]" />
        </a-form-item>

        <!-- 自定义流程：从已部署流程定义里动态选，支持业务人员新建的流程 -->
        <template v-if="isCustom">
          <a-form-item label="流程定义" required>
            <Select v-model:value="startForm.customKey" :options="defOptions"
              placeholder="选择已部署的流程定义" show-search option-filter-prop="label" />
            <div v-if="defOptions.length === 0" style="color: #fa8c16; font-size: 12px; margin-top: 4px">
              暂无已部署流程，请先到「流程设计器」新建并部署
            </div>
          </a-form-item>
          <a-form-item label="业务对象类型" required>
            <Select v-model:value="startForm.bizType" :options="bizTypeOpts"
              placeholder="选择该流程关联的业务对象" />
          </a-form-item>
          <!-- 关联员工/简历时，从对应列表中选定具体业务对象（即 bizId） -->
          <a-form-item v-if="customBizOptions.length" required
            :label="startForm.bizType === 'RESUME' ? '候选人简历' : '员工'">
            <Select v-model:value="startForm.bizId" :options="customBizOptions"
              :placeholder="startForm.bizType === 'RESUME' ? '请选择候选人简历' : '请选择员工'"
              show-search option-filter-prop="label" />
          </a-form-item>
          <a-form-item label="业务参数（可选）">
            <div v-for="(p, idx) in startForm.bizParams" :key="idx"
              style="display: flex; gap: 8px; margin-bottom: 8px">
              <a-input v-model:value="p.key" placeholder="变量名，如 targetDeptName" />
              <a-input v-model:value="p.value" placeholder="值" />
              <Button type="text" danger size="small" @click="removeBizParam(idx)">删除</Button>
            </div>
            <Button type="dashed" block size="small" @click="addBizParam">
              <PlusOutlined /> 添加参数
            </Button>
            <div style="color: rgba(0,0,0,0.45); font-size: 12px; margin-top: 4px">
              参数将作为流程变量传入，供 BPMN 中的 ${变量名} 取值
            </div>
          </a-form-item>
        </template>

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

        <!-- 离职/转正/调岗流程：选员工 -->
        <template v-if="isBizEmployee">
          <!-- 转正流程只能选实习/试用员工 -->
          <a-form-item v-if="isRegular" label="选择实习生" required>
            <Select v-model:value="startForm.bizId" :options="regularEmpOptions"
              placeholder="请选择实习/试用员工" show-search option-filter-prop="label" />
            <div v-if="regularEmpOptions.length === 0" style="color: #fa8c16; font-size: 12px; margin-top: 4px">
              当前无实习/试用期员工，暂无可转正人员
            </div>
          </a-form-item>
          <!-- 离职/调岗流程：选在职员工 -->
          <a-form-item v-else label="选择员工" required>
            <Select v-model:value="startForm.bizId" :options="empOptions"
              placeholder="请选择在职员工" show-search option-filter-prop="label" />
          </a-form-item>
          <!-- 调岗流程：目标部门/岗位（可选） -->
          <template v-if="isTransfer">
            <a-form-item label="目标部门（可选）">
              <a-input v-model:value="startForm.targetDeptName" placeholder="如：研发部" />
            </a-form-item>
            <a-form-item label="目标岗位（可选）">
              <a-input v-model:value="startForm.targetPosition" placeholder="如：Java工程师" />
            </a-form-item>
          </template>
        </template>
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

    <!-- 运行态流程图 -->
    <FlowDiagramModal v-model:open="diagramOpen" :flow-instance-id="diagramFlowId" :title="diagramTitle" />
  </div>
</template>