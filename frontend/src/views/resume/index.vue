<script setup lang="ts">
import { ref, computed, h, reactive } from 'vue'
import {
  Table, Button, Space, Input, InputNumber, Select, Form, Modal, Tag, message, App as AntApp, Descriptions
} from 'ant-design-vue'
import { PlusOutlined, SearchOutlined, ReloadOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { resumeApi, type Resume } from '@/api/modules/resume'
import { useCrud } from '@/composables/useCrud'

defineOptions({ name: 'ResumePage' })
const { modal } = AntApp.useApp()

const formRef = ref()
const queryForm = ref<Record<string, any>>({})
const mode = ref<'view' | 'edit' | 'create'>('create')
const visible = ref(false)
const current = ref<Resume | null>(null)
const formState = reactive<Partial<Resume>>({})
function resetFormState() {
  Object.keys(formState).forEach((k) => { delete (formState as any)[k] })
}

const statusOpts = [
  { label: '待筛选', value: 0 },
  { label: '已通过', value: 1 },
  { label: '已淘汰', value: 2 },
  { label: '面试中', value: 3 }
]
const statusColor: Record<number, string> = { 0: 'default', 1: 'green', 2: 'red', 3: 'blue' }

const crud = reactive(useCrud<Resume>(resumeApi))
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
function onView(r: Resume) { mode.value = 'view'; current.value = r; visible.value = true }
function onEdit(r: Resume) { resetFormState(); Object.assign(formState, r); mode.value = 'edit'; current.value = { ...r }; visible.value = true }
async function onSubmit() {
  try {
    await formRef.value.validate()
    if (mode.value === 'create') { await crud.save({ ...formState }); message.success('新增成功') }
    else if (mode.value === 'edit' && current.value?.id) { await crud.update(current.value.id, { ...formState }); message.success('更新成功') }
    visible.value = false
  } catch { /* validation */ }
}
function onDelete(r: Resume) {
  if (!r.id) return
  modal.confirm({ title: '确认删除', content: `确定删除简历「${r.resumeName}」吗？`,
    okText: '删除', okType: 'danger', cancelText: '取消',
    onOk: async () => { await crud.remove(r.id!); message.success('删除成功') } })
}
function statusText(s?: number) {
  return statusOpts.find(o => o.value === s)?.label ?? '-'
}

const columns = [
  { title: '简历名称', dataIndex: 'resumeName', width: 160 },
  { title: '目标岗位ID', dataIndex: 'targetJobId', width: 110 },
  { title: '匹配分数', dataIndex: 'matchScore', width: 110,
    customRender: ({ record }: any) => {
      const v = record.matchScore
      if (v == null) return '-'
      const color = v >= 80 ? '#52C41A' : v >= 60 ? '#FAAD14' : '#FF4D4F'
      return h('span', { style: { color, fontWeight: 600 } }, `${v}`)
    }
  },
  {
    title: '状态', dataIndex: 'resumeStatus', width: 100,
    customRender: ({ record }: any) => h(Tag, { color: statusColor[record.resumeStatus] || 'default' }, () => statusText(record.resumeStatus))
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
      <h2 class="page-title">简历管理</h2>
      <p class="page-subtitle">管理候选人简历与 AI 匹配分析结果</p>
    </div>

    <div class="search-card">
      <Form layout="inline" :model="queryForm" :label-col="{ style: { width: 80 } }">
        <Form.Item label="简历名称">
          <Input v-model:value="queryForm.resumeName" placeholder="请输入" allow-clear style="width: 180px" />
        </Form.Item>
        <Form.Item label="状态">
          <Select v-model:value="queryForm.resumeStatus" placeholder="全部" allow-clear style="width: 130px" :options="statusOpts" />
        </Form.Item>
        <Form.Item label="岗位ID">
          <InputNumber v-model:value="queryForm.targetJobId" placeholder="岗位ID" style="width: 140px" />
        </Form.Item>
        <Form.Item label="最低匹配分">
          <InputNumber v-model:value="queryForm.minMatchScore" :min="0" :max="100" placeholder="0-100" style="width: 140px" />
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
          <strong>简历列表</strong>
          <span style="color: rgba(0,0,0,0.45); font-size: 13px">共 {{ crud.total }} 条</span>
        </div>
        <div class="table-toolbar-right">
          <Button type="primary" :icon="h(PlusOutlined)" @click="onCreate">新增简历</Button>
        </div>
      </div>

      <Table :columns="columns" :data-source="safeDataSource" :loading="crud.loading"
        :pagination="pagination"
        :scroll="{ x: 900 }" row-key="id" @change="crud.handleTableChange" />
    </div>

    <Modal v-model:open="visible"
      :title="mode === 'create' ? '新增简历' : mode === 'edit' ? '编辑简历' : '简历详情'"
      :footer="mode === 'view' ? null : undefined" width="640px" destroy-on-close>
      <Form v-if="mode !== 'view'" ref="formRef" layout="vertical" :model="formState">
        <a-row :gutter="16">
          <a-col :span="12"><Form.Item label="简历名称" name="resumeName" :rules="[{ required: true, message: '请输入简历名称' }]">
            <Input v-model:value="formState.resumeName" placeholder="如 张三-前端工程师" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="意向岗位" name="expectPosition">
            <Input v-model:value="formState.expectPosition" placeholder="如 前端工程师" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="手机号" name="phone">
            <Input v-model:value="formState.phone" placeholder="请输入手机号" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="邮箱" name="email">
            <Input v-model:value="formState.email" placeholder="请输入邮箱" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="工作年限" name="workYears">
            <InputNumber v-model:value="formState.workYears" :min="0" style="width: 100%" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="毕业学校" name="school">
            <Input v-model:value="formState.school" placeholder="请输入毕业学校" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="目标岗位ID" name="targetJobId">
            <InputNumber v-model:value="formState.targetJobId" :min="0" style="width: 100%" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="匹配分数" name="matchScore">
            <InputNumber v-model:value="formState.matchScore" :min="0" :max="100" style="width: 100%" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="状态" name="resumeStatus">
            <Select v-model:value="formState.resumeStatus" :options="statusOpts" placeholder="请选择" />
          </Form.Item></a-col>
          <a-col :span="24"><Form.Item label="简历原始内容" name="resumeContent">
            <Input.TextArea v-model:value="formState.resumeContent" :rows="4" placeholder="简历原始文本内容" />
          </Form.Item></a-col>
          <a-col :span="24"><Form.Item label="AI解析结果(JSON)" name="resumeStructJson">
            <Input.TextArea v-model:value="formState.resumeStructJson" :rows="4" placeholder="AI 解析结果（JSON）" />
          </Form.Item></a-col>
        </a-row>
      </Form>
      <Descriptions v-else :column="2" bordered>
        <Descriptions.Item label="简历名称">{{ current?.resumeName || '-' }}</Descriptions.Item>
        <Descriptions.Item label="意向岗位">{{ current?.expectPosition || '-' }}</Descriptions.Item>
        <Descriptions.Item label="手机号">{{ current?.phone || '-' }}</Descriptions.Item>
        <Descriptions.Item label="邮箱">{{ current?.email || '-' }}</Descriptions.Item>
        <Descriptions.Item label="工作年限">{{ current?.workYears ?? '-' }}</Descriptions.Item>
        <Descriptions.Item label="毕业学校">{{ current?.school || '-' }}</Descriptions.Item>
        <Descriptions.Item label="目标岗位ID">{{ current?.targetJobId ?? '-' }}</Descriptions.Item>
        <Descriptions.Item label="匹配分数">{{ current?.matchScore ?? '-' }}</Descriptions.Item>
        <Descriptions.Item label="状态">{{ statusText(current?.resumeStatus) }}</Descriptions.Item>
        <Descriptions.Item label="简历原始内容" :span="2">{{ current?.resumeContent || '-' }}</Descriptions.Item>
        <Descriptions.Item label="AI解析结果" :span="2">{{ current?.resumeStructJson || '-' }}</Descriptions.Item>
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
