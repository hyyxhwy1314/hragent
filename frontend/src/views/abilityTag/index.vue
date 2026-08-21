<script setup lang="ts">
import { ref, computed, h, reactive } from 'vue'
import {
  Table, Button, Space, Input, InputNumber, Select, Form, Modal, Tag, message, App as AntApp, Descriptions
} from 'ant-design-vue'
import { PlusOutlined, SearchOutlined, ReloadOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { abilityTagApi, type AbilityTag } from '@/api/modules/abilityTag'
import { useCrud } from '@/composables/useCrud'

defineOptions({ name: 'AbilityTagPage' })
const { modal } = AntApp.useApp()

const formRef = ref()
const queryForm = ref<Record<string, any>>({})
const mode = ref<'view' | 'edit' | 'create'>('create')
const visible = ref(false)
const current = ref<AbilityTag | null>(null)
const formState = reactive<Partial<AbilityTag>>({})
function resetFormState() {
  Object.keys(formState).forEach((k) => { delete (formState as any)[k] })
}

const statusOpts = [
  { label: '启用', value: 1 },
  { label: '停用', value: 0 }
]
const statusColor: Record<number, string> = { 1: 'green', 0: 'default' }
const categoryOpts = [
  { label: '技术技能', value: '技术技能' },
  { label: '软技能', value: '软技能' },
  { label: '行业知识', value: '行业知识' },
  { label: '管理能力', value: '管理能力' }
]

const crud = reactive(useCrud<AbilityTag>(abilityTagApi))
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
function onView(r: AbilityTag) { mode.value = 'view'; current.value = r; visible.value = true }
function onEdit(r: AbilityTag) { resetFormState(); Object.assign(formState, r); mode.value = 'edit'; current.value = { ...r }; visible.value = true }
async function onSubmit() {
  try {
    await formRef.value.validate()
    if (mode.value === 'create') { await crud.save({ ...formState }); message.success('新增成功') }
    else if (mode.value === 'edit' && current.value?.id) { await crud.update(current.value.id, { ...formState }); message.success('更新成功') }
    visible.value = false
  } catch { /* validation */ }
}
function onDelete(r: AbilityTag) {
  if (!r.id) return
  modal.confirm({ title: '确认删除', content: `确定删除标签「${r.tagName}」吗？`,
    okText: '删除', okType: 'danger', cancelText: '取消',
    onOk: async () => { await crud.remove(r.id!); message.success('删除成功') } })
}
function statusText(s?: number) { return statusOpts.find(o => o.value === s)?.label ?? '-' }

const columns: any[] = [
  { title: '标签编码', dataIndex: 'tagCode', width: 140 },
  { title: '标签名称', dataIndex: 'tagName', width: 160 },
  { title: '分类', dataIndex: 'tagCategory', width: 130 },
  { title: '排序', dataIndex: 'sort', width: 80 },
  {
    title: '状态', dataIndex: 'status', width: 90,
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
      <h2 class="page-title">能力标签</h2>
      <p class="page-subtitle">维护能力标签库，用于简历解析与人才匹配</p>
    </div>

    <div class="search-card">
      <Form layout="inline" :model="queryForm" :label-col="{ style: { width: 72 } }">
        <Form.Item label="标签名称">
          <Input v-model:value="queryForm.tagName" placeholder="请输入" allow-clear style="width: 180px" />
        </Form.Item>
        <Form.Item label="分类">
          <Select v-model:value="queryForm.tagCategory" placeholder="全部" allow-clear style="width: 140px" :options="categoryOpts" />
        </Form.Item>
        <Form.Item label="状态">
          <Select v-model:value="queryForm.status" placeholder="全部" allow-clear style="width: 120px" :options="statusOpts" />
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
          <strong>标签列表</strong>
          <span style="color: rgba(0,0,0,0.45); font-size: 13px">共 {{ crud.total }} 条</span>
        </div>
        <div class="table-toolbar-right">
          <Button type="primary" :icon="h(PlusOutlined)" @click="onCreate">新增标签</Button>
        </div>
      </div>

      <Table :columns="columns" :data-source="safeDataSource" :loading="crud.loading"
        :pagination="pagination"
        :scroll="{ x: 900 }" row-key="id" @change="crud.handleTableChange" />
    </div>

    <Modal v-model:open="visible"
      :title="mode === 'create' ? '新增标签' : mode === 'edit' ? '编辑标签' : '标签详情'"
      :footer="mode === 'view' ? null : undefined" width="640px" destroy-on-close>
      <Form v-if="mode !== 'view'" ref="formRef" layout="vertical" :model="formState">
        <a-row :gutter="16">
          <a-col :span="12"><Form.Item label="标签编码" name="tagCode" :rules="[{ required: true, message: '请输入标签编码' }]">
            <Input v-model:value="formState.tagCode" placeholder="如 TAG-001" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="标签名称" name="tagName" :rules="[{ required: true, message: '请输入标签名称' }]">
            <Input v-model:value="formState.tagName" placeholder="如 Vue.js" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="分类" name="tagCategory" :rules="[{ required: true, message: '请选择分类' }]">
            <Select v-model:value="formState.tagCategory" :options="categoryOpts" placeholder="请选择" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="排序" name="sort">
            <InputNumber v-model:value="formState.sort" :min="0" style="width: 100%" placeholder="数字越小越靠前" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="状态" name="status">
            <Select v-model:value="formState.status" :options="statusOpts" placeholder="请选择" />
          </Form.Item></a-col>
        </a-row>
      </Form>
      <Descriptions v-else :column="2" bordered>
        <Descriptions.Item label="标签编码">{{ current?.tagCode || '-' }}</Descriptions.Item>
        <Descriptions.Item label="标签名称">{{ current?.tagName || '-' }}</Descriptions.Item>
        <Descriptions.Item label="分类">{{ current?.tagCategory || '-' }}</Descriptions.Item>
        <Descriptions.Item label="排序">{{ current?.sort ?? '-' }}</Descriptions.Item>
        <Descriptions.Item label="状态">{{ statusText(current?.status) }}</Descriptions.Item>
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
