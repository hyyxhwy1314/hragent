<script setup lang="ts">
import { ref, computed, h, reactive } from 'vue'
import {
  Table, Button, Space, Input, InputNumber, Select, Form, Modal, Tag, message, App as AntApp, Descriptions
} from 'ant-design-vue'
import { PlusOutlined, SearchOutlined, ReloadOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { performanceApi, type Performance } from '@/api/modules/performance'
import { useCrud } from '@/composables/useCrud'

defineOptions({ name: 'PerformancePage' })
const { modal } = AntApp.useApp()

const formRef = ref()
const queryForm = ref<Record<string, any>>({})
const mode = ref<'view' | 'edit' | 'create'>('create')
const visible = ref(false)
const current = ref<Performance | null>(null)

const statusOpts = [
  { label: '草稿', value: 0 },
  { label: '进行中', value: 1 },
  { label: '已完成', value: 2 },
  { label: '已归档', value: 3 }
]
const statusColor: Record<number, string> = { 0: 'default', 1: 'blue', 2: 'green', 3: 'orange' }
const rankOpts = ['S', 'A', 'B', 'C', 'D']

const crud = reactive(useCrud<Performance>(performanceApi))
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
function onCreate() { mode.value = 'create'; current.value = null; visible.value = true }
function onView(r: Performance) { mode.value = 'view'; current.value = r; visible.value = true }
function onEdit(r: Performance) { mode.value = 'edit'; current.value = { ...r }; visible.value = true }
async function onSubmit() {
  try {
    const vals = await formRef.value.validate()
    if (mode.value === 'create') { await crud.save(vals); message.success('新增成功') }
    else if (mode.value === 'edit' && current.value?.id) { await crud.update(current.value.id, vals); message.success('更新成功') }
    visible.value = false
  } catch { /* validation */ }
}
function onDelete(r: Performance) {
  if (!r.id) return
  modal.confirm({ title: '确认删除', content: `确定删除绩效记录吗？`,
    okText: '删除', okType: 'danger', cancelText: '取消',
    onOk: async () => { await crud.remove(r.id!); message.success('删除成功') } })
}
function statusText(s?: number) { return statusOpts.find(o => o.value === s)?.label ?? '-' }

const columns = [
  { title: '员工ID', dataIndex: 'empId', width: 100 },
  { title: '考核周期', dataIndex: 'periodCode', width: 130 },
  { title: 'KPI得分', dataIndex: 'kpiScore', width: 100 },
  { title: '态度得分', dataIndex: 'attitudeScore', width: 100 },
  { title: '能力得分', dataIndex: 'abilityScore', width: 100 },
  {
    title: '综合得分', dataIndex: 'overallScore', width: 110,
    customRender: ({ record }: any) => h('span', { style: { fontWeight: 600, color: '#2F54EB' } }, record.overallScore ?? '-')
  },
  { title: '等级', dataIndex: 'rankLevel', width: 80 },
  {
    title: '状态', dataIndex: 'status', width: 100,
    customRender: ({ record }: any) => h(Tag, { color: statusColor[record.status] || 'default' }, () => statusText(record.status))
  },
  {
    title: '操作', key: 'action', width: 180, fixed: 'right',
    customRender: ({ record }: any) => h(Space, {}, () => [
      h(Button, { size: 'small', type: 'link', onClick: () => onView(record) }, () => '查看'),
      h(Button, { size: 'small', type: 'link', onClick: () => onEdit(record) }, () => '编辑'),
      h(Button, { size: 'small', type: 'link', danger: true, onClick: () => onDelete(record) }, () => '删除')
    ])
  }
]
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">绩效管理</h2>
      <p class="page-subtitle">记录员工绩效考核与评估结果</p>
    </div>

    <div class="search-card">
      <Form layout="inline" :model="queryForm" :label-col="{ style: { width: 72 } }">
        <Form.Item label="员工ID">
          <InputNumber v-model:value="queryForm.empId" placeholder="员工ID" style="width: 140px" />
        </Form.Item>
        <Form.Item label="周期">
          <Input v-model:value="queryForm.periodCode" placeholder="如 2025-Q1" allow-clear style="width: 160px" />
        </Form.Item>
        <Form.Item label="状态">
          <Select v-model:value="queryForm.status" placeholder="全部" allow-clear style="width: 130px" :options="statusOpts" />
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
          <strong>绩效列表</strong>
          <span style="color: rgba(0,0,0,0.45); font-size: 13px">共 {{ crud.total }} 条</span>
        </div>
        <div class="table-toolbar-right">
          <Button type="primary" :icon="h(PlusOutlined)" @click="onCreate">新增绩效</Button>
        </div>
      </div>

      <Table :columns="columns" :data-source="safeDataSource" :loading="crud.loading"
        :pagination="pagination"
        :scroll="{ x: 1100 }" row-key="id" @change="crud.handleTableChange" />
    </div>

    <Modal v-model:open="visible"
      :title="mode === 'create' ? '新增绩效' : mode === 'edit' ? '编辑绩效' : '绩效详情'"
      :footer="mode === 'view' ? null : undefined" width="640px" destroy-on-close>
      <Form v-if="mode !== 'view'" ref="formRef" layout="vertical" :initial-values="mode === 'edit' ? current : {}">
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px">
          <Form.Item label="员工ID" name="empId" :rules="[{ required: true, message: '请输入员工ID' }]">
            <InputNumber :min="1" style="width: 100%" />
          </Form.Item>
          <Form.Item label="考核周期" name="periodCode" :rules="[{ required: true, message: '请输入考核周期' }]">
            <Input placeholder="如 2025-Q1" />
          </Form.Item>
          <Form.Item label="KPI得分" name="kpiScore"><InputNumber :min="0" :max="100" style="width: 100%" /></Form.Item>
          <Form.Item label="态度得分" name="attitudeScore"><InputNumber :min="0" :max="100" style="width: 100%" /></Form.Item>
          <Form.Item label="能力得分" name="abilityScore"><InputNumber :min="0" :max="100" style="width: 100%" /></Form.Item>
          <Form.Item label="综合得分" name="overallScore"><InputNumber :min="0" :max="100" style="width: 100%" /></Form.Item>
          <Form.Item label="等级" name="rankLevel">
            <Select :options="rankOpts.map(v => ({ label: v, value: v }))" placeholder="请选择" />
          </Form.Item>
          <Form.Item label="状态" name="status"><Select :options="statusOpts" placeholder="请选择" /></Form.Item>
          <Form.Item label="评语" name="comment" :span="2">
            <Input.TextArea :rows="3" placeholder="请输入评语" />
          </Form.Item>
        </div>
      </Form>
      <Descriptions v-else :column="2" bordered :items="[
        { key: 'empId', label: '员工ID', children: current?.empId },
        { key: 'periodCode', label: '考核周期', children: current?.periodCode },
        { key: 'kpiScore', label: 'KPI得分', children: current?.kpiScore },
        { key: 'attitudeScore', label: '态度得分', children: current?.attitudeScore },
        { key: 'abilityScore', label: '能力得分', children: current?.abilityScore },
        { key: 'overallScore', label: '综合得分', children: current?.overallScore },
        { key: 'rankLevel', label: '等级', children: current?.rankLevel },
        { key: 'status', label: '状态', children: statusText(current?.status) },
        { key: 'comment', label: '评语', children: current?.comment, span: 2 }
      ]" />
      <template #footer v-if="mode !== 'view'">
        <Space>
          <Button @click="visible = false">取消</Button>
          <Button type="primary" @click="onSubmit">确定</Button>
        </Space>
      </template>
    </Modal>
  </div>
</template>
