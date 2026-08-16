<script setup lang="ts">
import { ref, computed, h, reactive } from 'vue'
import {
  Table, Button, Space, Input, InputNumber, Select, Form, Modal, Tag, message, App as AntApp, Descriptions
} from 'ant-design-vue'
import { PlusOutlined, SearchOutlined, ReloadOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { resumeAbilityRelApi, type ResumeAbilityRel } from '@/api/modules/resumeAbilityRel'
import { useCrud } from '@/composables/useCrud'

defineOptions({ name: 'ResumeAbilityRelPage' })
const { modal } = AntApp.useApp()

const formRef = ref()
const queryForm = ref<Record<string, any>>({})
const mode = ref<'view' | 'edit' | 'create'>('create')
const visible = ref(false)
const current = ref<ResumeAbilityRel | null>(null)
const formState = reactive<Partial<ResumeAbilityRel>>({})
function resetFormState() {
  Object.keys(formState).forEach((k) => { delete (formState as any)[k] })
}

const sourceOpts = [
  { label: 'AI 提取', value: 'AI' },
  { label: '人工标注', value: 'MANUAL' },
  { label: '候选人自评', value: 'SELF' }
]

const crud = reactive(useCrud<ResumeAbilityRel>(resumeAbilityRelApi))
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
function onView(r: ResumeAbilityRel) { mode.value = 'view'; current.value = r; visible.value = true }
function onEdit(r: ResumeAbilityRel) { resetFormState(); Object.assign(formState, r); mode.value = 'edit'; current.value = { ...r }; visible.value = true }
async function onSubmit() {
  try {
    await formRef.value.validate()
    if (mode.value === 'create') { await crud.save({ ...formState }); message.success('新增成功') }
    else if (mode.value === 'edit' && current.value?.id) { await crud.update(current.value.id, { ...formState }); message.success('更新成功') }
    visible.value = false
  } catch { /* validation */ }
}
function onDelete(r: ResumeAbilityRel) {
  if (!r.id) return
  modal.confirm({ title: '确认删除', content: `确定删除该关联记录吗？`,
    okText: '删除', okType: 'danger', cancelText: '取消',
    onOk: async () => { await crud.remove(r.id!); message.success('删除成功') } })
}

function confidenceColor(v?: number) {
  if (v == null) return 'default'
  if (v >= 0.8) return 'green'
  if (v >= 0.5) return 'orange'
  return 'red'
}

const columns = [
  { title: '简历ID', dataIndex: 'resumeId', width: 110 },
  { title: '标签ID', dataIndex: 'abilityTagId', width: 110 },
  {
    title: '置信度', dataIndex: 'confidence', width: 120,
    customRender: ({ record }: any) => {
      const v = record.confidence
      if (v == null) return '-'
      return h(Tag, { color: confidenceColor(v) }, () => `${(v * 100).toFixed(0)}%`)
    }
  },
  {
    title: '来源', dataIndex: 'source', width: 120,
    customRender: ({ record }: any) => {
      const label = sourceOpts.find(o => o.value === record.source)?.label || record.source
      const colorMap: Record<string, string> = { AI: 'blue', MANUAL: 'green', SELF: 'purple' }
      return h(Tag, { color: colorMap[record.source] || 'default' }, () => label || '-')
    }
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
      <h2 class="page-title">简历能力关联</h2>
      <p class="page-subtitle">管理简历与能力标签的匹配关系</p>
    </div>

    <div class="search-card">
      <Form layout="inline" :model="queryForm" :label-col="{ style: { width: 72 } }">
        <Form.Item label="简历ID">
          <InputNumber v-model:value="queryForm.resumeId" placeholder="简历ID" style="width: 140px" />
        </Form.Item>
        <Form.Item label="标签ID">
          <InputNumber v-model:value="queryForm.abilityTagId" placeholder="标签ID" style="width: 140px" />
        </Form.Item>
        <Form.Item label="来源">
          <Select v-model:value="queryForm.source" placeholder="全部" allow-clear style="width: 140px" :options="sourceOpts" />
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
          <strong>关联列表</strong>
          <span style="color: rgba(0,0,0,0.45); font-size: 13px">共 {{ crud.total }} 条</span>
        </div>
        <div class="table-toolbar-right">
          <Button type="primary" :icon="h(PlusOutlined)" @click="onCreate">新增关联</Button>
        </div>
      </div>

      <Table :columns="columns" :data-source="safeDataSource" :loading="crud.loading"
        :pagination="pagination"
        :scroll="{ x: 700 }" row-key="id" @change="crud.handleTableChange" />
    </div>

    <Modal v-model:open="visible"
      :title="mode === 'create' ? '新增关联' : mode === 'edit' ? '编辑关联' : '关联详情'"
      :footer="mode === 'view' ? null : undefined" width="520px" destroy-on-close>
      <Form v-if="mode !== 'view'" ref="formRef" layout="vertical" :model="formState">
        <a-row :gutter="16">
          <a-col :span="12"><Form.Item label="简历ID" name="resumeId" :rules="[{ required: true, message: '请输入简历ID' }]">
            <InputNumber v-model:value="formState.resumeId" :min="1" style="width: 100%" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="标签ID" name="abilityTagId" :rules="[{ required: true, message: '请输入标签ID' }]">
            <InputNumber v-model:value="formState.abilityTagId" :min="1" style="width: 100%" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="置信度" name="confidence">
            <InputNumber v-model:value="formState.confidence" :min="0" :max="1" :step="0.05" style="width: 100%" placeholder="0-1" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="来源" name="source">
            <Select v-model:value="formState.source" :options="sourceOpts" placeholder="请选择" />
          </Form.Item></a-col>
        </a-row>
      </Form>
      <Descriptions v-else :column="2" bordered>
        <Descriptions.Item label="简历ID">{{ current?.resumeId ?? '-' }}</Descriptions.Item>
        <Descriptions.Item label="标签ID">{{ current?.abilityTagId ?? '-' }}</Descriptions.Item>
        <Descriptions.Item label="置信度">{{ current?.confidence != null ? (current.confidence * 100).toFixed(0) + '%' : '-' }}</Descriptions.Item>
        <Descriptions.Item label="来源">{{ sourceOpts.find(o => o.value === current?.source)?.label || current?.source || '-' }}</Descriptions.Item>
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
