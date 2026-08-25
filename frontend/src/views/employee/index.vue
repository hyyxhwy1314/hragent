<script setup lang="ts">
import { ref, computed, h, reactive } from 'vue'
import {
  Table, Button, Space, Input, Select, Form, Modal, Tag, message, App as AntApp, Descriptions,
  Row as ARow, Col as ACol
} from 'ant-design-vue'
import { PlusOutlined, SearchOutlined, ReloadOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { employeeApi, type Employee } from '@/api/modules/employee'
import { useCrud } from '@/composables/useCrud'
import { startProcess } from '@/api/modules/flow'

defineOptions({ name: 'EmployeePage' })
const { modal } = AntApp.useApp()

const formRef = ref()
const queryForm = ref<Record<string, any>>({})
const mode = ref<'view' | 'edit' | 'create'>('create')
const visible = ref(false)
const current = ref<Employee | null>(null)
const formState = reactive<Partial<Employee>>({})
function resetFormState() {
  Object.keys(formState).forEach((k) => { delete (formState as any)[k] })
}

const statusOpts = [
  { label: '在职', value: 1 },
  { label: '离职', value: 0 },
  { label: '试用', value: 2 }
]
const statusColor: Record<number, string> = { 1: 'green', 0: 'red', 2: 'orange' }
const genderOpts = [
  { label: '男', value: 1 },
  { label: '女', value: 0 }
]
const roleOpts = [
  { label: '普通员工', value: 'EMPLOYEE' },
  { label: '部门主管', value: 'DEPT_LEADER' },
  { label: '人事', value: 'HR' },
  { label: 'HRBP', value: 'HRBP' },
  { label: '管理员', value: 'ADMIN' }
]
const roleColor: Record<string, string> = {
  EMPLOYEE: 'default', DEPT_LEADER: 'blue', HR: 'green', HRBP: 'purple', ADMIN: 'red'
}
const roleText = (r?: string) => roleOpts.find(o => o.value === r)?.label ?? '-'

// 直属上级候选：拉取员工列表用于下拉选择
const leaderOptions = ref<{ label: string; value: number }[]>([])
async function loadLeaders() {
  try {
    const list = await employeeApi.list()
    leaderOptions.value = (list || [])
      .filter((e: Employee) => e.id && e.empName)
      .map((e: Employee) => ({ label: `${e.empName}（${e.empNo || '-'}）`, value: e.id! }))
  } catch {}
}
loadLeaders()

// 根据 leaderId 查姓名（列表展示用）
const leaderNameMap = computed(() => {
  const m: Record<number, string> = {}
  leaderOptions.value.forEach(o => { m[o.value] = o.label })
  return m
})

const crud = reactive(useCrud<Employee>(employeeApi))
crud.fetch()

const safeDataSource = computed(() =>
  Array.isArray(crud.dataSource) ? crud.dataSource : []
)

const pagination = computed(() => ({
  current: crud.pageNum,
  pageSize: crud.pageSize,
  total: crud.total,
  showSizeChanger: true,
  showQuickJumper: true,
  showTotal: (t: number) => `共 ${t} 条`
}))

function onSearch() { crud.reload({ ...queryForm.value }) }
function onReset() { queryForm.value = {}; crud.reload() }
function onCreate() {
  resetFormState()
  mode.value = 'create'
  current.value = null
  visible.value = true
}
function onView(r: Employee) { mode.value = 'view'; current.value = r; visible.value = true }
function onEdit(r: Employee) {
  resetFormState()
  Object.assign(formState, r)
  mode.value = 'edit'
  current.value = { ...r }
  visible.value = true
}

async function onSubmit() {
  try {
    await formRef.value.validate()
    if (mode.value === 'create') {
      await crud.save({ ...formState })
      message.success('新增成功')
    } else if (mode.value === 'edit' && current.value?.id) {
      await crud.update(current.value.id, { ...formState })
      message.success('更新成功')
    }
    visible.value = false
  } catch (e) {
    /* validation */
  }
}
function onDelete(r: Employee) {
  if (!r.id) return
  modal.confirm({
    title: '确认删除', content: `确定删除员工「${r.empName}」吗？`,
    okText: '删除', okType: 'danger', cancelText: '取消',
    onOk: async () => { await crud.remove(r.id!); message.success('删除成功') }
  })
}

function onApplyLeave(r: Employee) {
  if (!r.id) return
  if (r.empStatus === 0) {
    message.warning('该员工已离职，无需申请')
    return
  }
  modal.confirm({
    title: '申请离职', content: `确定为员工「${r.empName}」发起离职流程吗？`,
    okText: '确定', okType: 'primary', cancelText: '取消',
    onOk: async () => {
      try {
        await startProcess({
          processKey: 'leave-process',
          bizId: r.id!
        })
        message.success('离职流程已发起')
      } catch {
        message.error('流程发起失败')
      }
    }
  })
}
function statusText(s?: number) { return statusOpts.find(o => o.value === s)?.label ?? '-' }
function genderText(g?: number) { return genderOpts.find(o => o.value === g)?.label ?? '-' }

const columns: any[] = [
  { title: '工号', dataIndex: 'empNo', width: 110 },
  { title: '姓名', dataIndex: 'empName', width: 110 },
  {
    title: '性别', dataIndex: 'gender', width: 70,
    customRender: ({ record }: any) => genderText(record.gender)
  },
  { title: '部门', dataIndex: 'deptName', width: 140 },
  { title: '职位', dataIndex: 'positionName', width: 140 },
  {
    title: '角色', dataIndex: 'role', width: 100,
    customRender: ({ record }: any) => h(Tag, { color: roleColor[record.role] || 'default' }, () => roleText(record.role))
  },
  {
    title: '直属上级', dataIndex: 'leaderId', width: 130,
    customRender: ({ record }: any) => leaderNameMap.value[record.leaderId]?.split('（')[0] || '-'
  },
  { title: '手机号', dataIndex: 'phone', width: 130 },
  { title: '入职日期', dataIndex: 'entryDate', width: 120 },
  {
    title: '状态', dataIndex: 'empStatus', width: 90,
    customRender: ({ record }: any) => h(Tag, { color: statusColor[record.empStatus] || 'default' }, () => statusText(record.empStatus))
  },
  {
    title: '操作', key: 'action', width: 250, fixed: 'right',
    customRender: ({ record }: any) => {
      const buttons = [
        h(Button, { size: 'small', type: 'link', onClick: () => onView(record) }, () => '查看'),
        h(Button, { size: 'small', type: 'link', onClick: () => onEdit(record) }, () => '编辑')
      ]
      if (record.empStatus !== 0) {
        buttons.push(h(Button, { size: 'small', type: 'link', onClick: () => onApplyLeave(record) }, () => '申请离职'))
      }
      buttons.push(h(Button, { size: 'small', type: 'link', danger: true, onClick: () => onDelete(record) }, () => '删除'))
      return h(Space, {}, () => buttons)
    }
  }
]
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">员工管理</h2>
      <p class="page-subtitle">管理企业全部员工档案信息</p>
    </div>

    <div class="search-card">
      <Form layout="inline" :model="queryForm" :label-col="{ style: { width: 60 } }">
        <Form.Item label="工号">
          <Input v-model:value="queryForm.empNo" placeholder="请输入工号" allow-clear style="width: 160px" />
        </Form.Item>
        <Form.Item label="姓名">
          <Input v-model:value="queryForm.empName" placeholder="请输入姓名" allow-clear style="width: 160px" />
        </Form.Item>
        <Form.Item label="状态">
          <Select v-model:value="queryForm.empStatus" placeholder="全部" allow-clear style="width: 120px"
            :options="statusOpts" />
        </Form.Item>
        <Form.Item>
          <Space>
            <Button type="primary" :icon="h(SearchOutlined)" @click="onSearch">查询</Button>
            <Button :icon="h(ReloadOutlined)" @click="onReset">重置</Button>
          </Space>
        </Form.Item>
      </Form>
    </div>

    <div class="table-card">
      <div class="table-toolbar">
        <div class="table-toolbar-left">
          <strong>员工列表</strong>
          <span style="color: rgba(0,0,0,0.45); font-size: 13px">共 {{ crud.total }} 条</span>
        </div>
        <div class="table-toolbar-right">
          <Button type="primary" :icon="h(PlusOutlined)" @click="onCreate">新增员工</Button>
        </div>
      </div>

      <Table :columns="columns" :data-source="safeDataSource" :loading="crud.loading"
        :pagination="pagination"
        :scroll="{ x: 1100 }" row-key="id" @change="crud.handleTableChange" />
    </div>

    <Modal v-model:open="visible"
      :title="mode === 'create' ? '新增员工' : mode === 'edit' ? '编辑员工' : '员工详情'"
      :footer="mode === 'view' ? null : undefined" width="720px" destroy-on-close>
      <Form v-if="mode !== 'view'" ref="formRef" layout="vertical" :model="formState" :initial-values="mode === 'edit' ? current : {}">
        <a-row :gutter="16">
          <a-col :span="12"><Form.Item label="工号" name="empNo" :rules="[{ required: true, message: '请输入工号' }]">
            <Input v-model:value="formState.empNo" placeholder="如 EMP001" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="姓名" name="empName" :rules="[{ required: true, message: '请输入姓名' }]">
            <Input v-model:value="formState.empName" placeholder="请输入姓名" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="性别" name="gender">
            <Select v-model:value="formState.gender" :options="genderOpts" placeholder="请选择" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="出生日期" name="birthDate">
            <Input v-model:value="formState.birthDate" placeholder="YYYY-MM-DD" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="手机号" name="phone">
            <Input v-model:value="formState.phone" placeholder="请输入手机号" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="邮箱" name="email">
            <Input v-model:value="formState.email" placeholder="请输入邮箱" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="部门" name="deptName">
            <Input v-model:value="formState.deptName" placeholder="请输入部门" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="职位" name="positionName">
            <Input v-model:value="formState.positionName" placeholder="请输入职位" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="入职日期" name="entryDate">
            <Input v-model:value="formState.entryDate" placeholder="YYYY-MM-DD" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="转正日期" name="regularDate">
            <Input v-model:value="formState.regularDate" placeholder="YYYY-MM-DD" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="离职日期" name="leaveDate">
            <Input v-model:value="formState.leaveDate" placeholder="YYYY-MM-DD" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="状态" name="empStatus">
            <Select v-model:value="formState.empStatus" :options="statusOpts" placeholder="请选择" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="工作城市" name="workCity">
            <Input v-model:value="formState.workCity" placeholder="请输入工作城市" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="角色" name="role">
            <Select v-model:value="formState.role" :options="roleOpts" placeholder="请选择角色" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="直属上级" name="leaderId">
            <Select v-model:value="formState.leaderId" :options="leaderOptions" placeholder="请选择直属上级" allow-clear show-search option-filter-prop="label" />
          </Form.Item></a-col>
          <a-col :span="24"><Form.Item label="备注" name="remark">
            <Input.TextArea v-model:value="formState.remark" :rows="3" placeholder="请输入备注" />
          </Form.Item></a-col>
        </a-row>
      </Form>
      <Descriptions v-else :column="2" bordered>
        <Descriptions.Item label="工号">{{ current?.empNo || '-' }}</Descriptions.Item>
        <Descriptions.Item label="姓名">{{ current?.empName || '-' }}</Descriptions.Item>
        <Descriptions.Item label="性别">{{ genderText(current?.gender) }}</Descriptions.Item>
        <Descriptions.Item label="出生日期">{{ current?.birthDate || '-' }}</Descriptions.Item>
        <Descriptions.Item label="手机号">{{ current?.phone || '-' }}</Descriptions.Item>
        <Descriptions.Item label="邮箱">{{ current?.email || '-' }}</Descriptions.Item>
        <Descriptions.Item label="部门">{{ current?.deptName || '-' }}</Descriptions.Item>
        <Descriptions.Item label="职位">{{ current?.positionName || '-' }}</Descriptions.Item>
        <Descriptions.Item label="入职日期">{{ current?.entryDate || '-' }}</Descriptions.Item>
        <Descriptions.Item label="转正日期">{{ current?.regularDate || '-' }}</Descriptions.Item>
        <Descriptions.Item label="离职日期">{{ current?.leaveDate || '-' }}</Descriptions.Item>
        <Descriptions.Item label="状态">{{ statusText(current?.empStatus) }}</Descriptions.Item>
        <Descriptions.Item label="工作城市">{{ current?.workCity || '-' }}</Descriptions.Item>
        <Descriptions.Item label="角色">{{ roleText(current?.role) }}</Descriptions.Item>
        <Descriptions.Item label="直属上级">{{ leaderNameMap[current?.leaderId ?? 0]?.split('（')[0] || '-' }}</Descriptions.Item>
        <Descriptions.Item label="备注" :span="2">{{ current?.remark || '-' }}</Descriptions.Item>
      </Descriptions>
      <template #footer v-if="mode !== 'view'">
        <Space>
          <Button @click="visible = false">取消</Button>
          <Button type="primary" @click="onSubmit">确定</Button>
        </Space>
      </template>
    </Modal>
  </div>
</template>
