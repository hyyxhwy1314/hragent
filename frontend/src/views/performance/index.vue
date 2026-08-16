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
const formState = reactive<Partial<Performance>>({})
function resetFormState() {
  Object.keys(formState).forEach((k) => { delete (formState as any)[k] })
}

const statusOpts = [
  { label: '草稿', value: 0 },
  { label: '待审核', value: 1 },
  { label: '已完成', value: 2 },
  { label: '已驳回', value: 3 }
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
function onCreate() { resetFormState(); mode.value = 'create'; current.value = null; visible.value = true }
function onView(r: Performance) { mode.value = 'view'; current.value = r; visible.value = true }
function onEdit(r: Performance) { resetFormState(); Object.assign(formState, r); mode.value = 'edit'; current.value = { ...r }; visible.value = true }

async function onSubmit() {
  try {
    await formRef.value.validate()
    const payload: any = { ...formState }
    if (mode.value === 'create') { await crud.save(payload); message.success('新增成功') }
    else if (mode.value === 'edit' && current.value?.id) { await crud.update(current.value.id, payload); message.success('更新成功') }
    visible.value = false
  } catch { /* validation */ }
}
function onDelete(r: Performance) {
  if (!r.id) return
  modal.confirm({
    title: '确认删除', content: `确定删除绩效记录吗？`,
    okText: '删除', okType: 'danger', cancelText: '取消',
    onOk: async () => { await crud.remove(r.id!); message.success('删除成功') }
  })
}
function statusText(s?: number) { return statusOpts.find(o => o.value === s)?.label ?? '-' }

const columns = [
  { title: '员工ID', dataIndex: 'empId', width: 100 },
  { title: '考核周期', dataIndex: 'periodCode', width: 130 },
  { title: '自评分数', dataIndex: 'selfScore', width: 110 },
  { title: '领导评分', dataIndex: 'leaderScore', width: 110 },
  {
    title: '最终得分', dataIndex: 'finalScore', width: 110,
    customRender: ({ record }: any) => h('span', { style: { fontWeight: 600, color: '#2F54EB' } }, record.finalScore ?? '-')
  },
  { title: '绩效等级', dataIndex: 'performanceLevel', width: 100 },
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
          <Input v-model:value="queryForm.periodCode" placeholder="如 2026-Q3" allow-clear style="width: 160px" />
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
      <Form v-if="mode !== 'view'" ref="formRef" layout="vertical" :model="formState">
        <a-row :gutter="16">
          <a-col :span="12"><Form.Item label="员工ID" name="empId" :rules="[{ required: true, message: '请输入员工ID' }]">
            <InputNumber v-model:value="formState.empId" :min="1" style="width: 100%" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="考核周期" name="periodCode" :rules="[{ required: true, message: '请输入考核周期' }]">
            <Input v-model:value="formState.periodCode" placeholder="如 2026-Q3" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="自评分数" name="selfScore">
            <InputNumber v-model:value="formState.selfScore" :min="0" :max="100" :precision="2" style="width: 100%" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="直属领导评分" name="leaderScore">
            <InputNumber v-model:value="formState.leaderScore" :min="0" :max="100" :precision="2" style="width: 100%" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="最终得分" name="finalScore">
            <InputNumber v-model:value="formState.finalScore" :min="0" :max="100" :precision="2" style="width: 100%" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="绩效等级" name="performanceLevel">
            <Select v-model:value="formState.performanceLevel"
              :options="rankOpts.map(v => ({ label: v, value: v }))"
              placeholder="请选择" allow-clear />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="单据状态" name="status">
            <Select v-model:value="formState.status" :options="statusOpts" placeholder="请选择" allow-clear />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="流程实例ID" name="flowInstanceId">
            <InputNumber v-model:value="formState.flowInstanceId" :min="1" style="width: 100%" />
          </Form.Item></a-col>
          <a-col :span="24"><Form.Item label="KPI指标JSON" name="kpiJson">
            <Input.TextArea v-model:value="formState.kpiJson" :rows="2"
              placeholder='如 {"销售额": 100, "客户满意度": 95}' />
          </Form.Item></a-col>
          <a-col :span="24"><Form.Item label="AI评语" name="aiComment">
            <Input.TextArea v-model:value="formState.aiComment" :rows="3" placeholder="请输入/生成评语" />
          </Form.Item></a-col>
        </a-row>
      </Form>
      <Descriptions v-else :column="2" bordered>
        <Descriptions.Item label="员工ID">{{ current?.empId ?? '-' }}</Descriptions.Item>
        <Descriptions.Item label="考核周期">{{ current?.periodCode || '-' }}</Descriptions.Item>
        <Descriptions.Item label="自评分数">{{ current?.selfScore ?? '-' }}</Descriptions.Item>
        <Descriptions.Item label="领导评分">{{ current?.leaderScore ?? '-' }}</Descriptions.Item>
        <Descriptions.Item label="最终得分">{{ current?.finalScore ?? '-' }}</Descriptions.Item>
        <Descriptions.Item label="绩效等级">{{ current?.performanceLevel || '-' }}</Descriptions.Item>
        <Descriptions.Item label="状态">{{ statusText(current?.status) }}</Descriptions.Item>
        <Descriptions.Item label="流程实例ID">{{ current?.flowInstanceId ?? '-' }}</Descriptions.Item>
        <Descriptions.Item label="KPI指标" :span="2">{{ current?.kpiJson || '-' }}</Descriptions.Item>
        <Descriptions.Item label="AI评语" :span="2">{{ current?.aiComment || '-' }}</Descriptions.Item>
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
