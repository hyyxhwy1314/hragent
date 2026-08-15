<script setup lang="ts">
import { ref, computed, h, reactive } from 'vue'
import {
  Table, Button, Space, Input, InputNumber, Select, Form, Modal, Tag, message, App as AntApp, Descriptions
} from 'ant-design-vue'
import {
  PlusOutlined, SearchOutlined, ReloadOutlined, EditOutlined, DeleteOutlined
} from '@ant-design/icons-vue'
import { jobPostApi, type JobPost } from '@/api/modules/jobPost'
import { useCrud } from '@/composables/useCrud'

defineOptions({ name: 'JobPostPage' })
const { modal } = AntApp.useApp()

const formRef = ref()
const queryForm = ref<Record<string, any>>({})
const mode = ref<'view' | 'edit' | 'create'>('create')
const visible = ref(false)
const current = ref<JobPost | null>(null)

const statusOpts = [
  { label: '开放', value: 1 },
  { label: '关闭', value: 0 },
  { label: '暂停', value: 2 }
]
const statusColor: Record<number, string> = { 1: 'green', 0: 'default', 2: 'orange' }

const crud = reactive(useCrud<JobPost>(jobPostApi))
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
function onView(r: JobPost) { mode.value = 'view'; current.value = r; visible.value = true }
function onEdit(r: JobPost) { mode.value = 'edit'; current.value = { ...r }; visible.value = true }

async function onSubmit() {
  try {
    const vals = await formRef.value.validate()
    if (mode.value === 'create') { await crud.save(vals); message.success('新增成功') }
    else if (mode.value === 'edit' && current.value?.id) { await crud.update(current.value.id, vals); message.success('更新成功') }
    visible.value = false
  } catch { /* validation */ }
}
function onDelete(r: JobPost) {
  if (!r.id) return
  modal.confirm({
    title: '确认删除', content: `确定删除岗位「${r.jobName}」吗？`,
    okText: '删除', okType: 'danger', cancelText: '取消',
    onOk: async () => { await crud.remove(r.id!); message.success('删除成功') }
  })
}
function statusText(s?: number) {
  if (s === 1) return '开放'
  if (s === 0) return '关闭'
  if (s === 2) return '暂停'
  return '-'
}

const columns = [
  { title: '岗位编码', dataIndex: 'jobCode', width: 120 },
  { title: '岗位名称', dataIndex: 'jobName', width: 160 },
  { title: '所属部门', dataIndex: 'deptName', width: 140 },
  { title: '招聘人数', dataIndex: 'headcount', width: 100 },
  {
    title: '状态', dataIndex: 'jobStatus', width: 90,
    customRender: ({ record }: any) => h(Tag, { color: statusColor[record.jobStatus] || 'default' }, () => statusText(record.jobStatus))
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
      <h2 class="page-title">岗位管理</h2>
      <p class="page-subtitle">维护企业招聘岗位及需求信息</p>
    </div>

    <div class="search-card">
      <Form layout="inline" :model="queryForm" :label-col="{ style: { width: 72 } }">
        <Form.Item label="岗位编码">
          <Input v-model:value="queryForm.jobCode" placeholder="请输入" allow-clear style="width: 160px" />
        </Form.Item>
        <Form.Item label="岗位名称">
          <Input v-model:value="queryForm.jobName" placeholder="请输入" allow-clear style="width: 160px" />
        </Form.Item>
        <Form.Item label="部门">
          <Input v-model:value="queryForm.deptName" placeholder="请输入" allow-clear style="width: 160px" />
        </Form.Item>
        <Form.Item label="状态">
          <Select v-model:value="queryForm.jobStatus" placeholder="全部" allow-clear style="width: 120px" :options="statusOpts" />
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
          <strong>岗位列表</strong>
          <span style="color: rgba(0,0,0,0.45); font-size: 13px">共 {{ crud.total }} 条</span>
        </div>
        <div class="table-toolbar-right">
          <Button type="primary" :icon="h(PlusOutlined)" @click="onCreate">新增岗位</Button>
        </div>
      </div>

      <Table :columns="columns" :data-source="safeDataSource" :loading="crud.loading"
        :pagination="pagination"
        :scroll="{ x: 900 }" row-key="id" @change="crud.handleTableChange" />
    </div>

    <Modal v-model:open="visible"
      :title="mode === 'create' ? '新增岗位' : mode === 'edit' ? '编辑岗位' : '岗位详情'"
      :footer="mode === 'view' ? null : undefined" width="640px" destroy-on-close>
      <Form v-if="mode !== 'view'" ref="formRef" layout="vertical" :initial-values="mode === 'edit' ? current : {}">
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px">
          <Form.Item label="岗位编码" name="jobCode" :rules="[{ required: true, message: '请输入岗位编码' }]">
            <Input placeholder="如 JOB-001" />
          </Form.Item>
          <Form.Item label="岗位名称" name="jobName" :rules="[{ required: true, message: '请输入岗位名称' }]">
            <Input placeholder="如 高级前端工程师" />
          </Form.Item>
          <Form.Item label="所属部门" name="deptName">
            <Input placeholder="请输入部门" />
          </Form.Item>
          <Form.Item label="招聘人数" name="headcount">
            <InputNumber :min="0" style="width: 100%" placeholder="请输入招聘人数" />
          </Form.Item>
          <Form.Item label="状态" name="jobStatus">
            <Select :options="statusOpts" placeholder="请选择" />
          </Form.Item>
          <Form.Item label="岗位描述" name="jobDesc" :span="2">
            <Input.TextArea :rows="3" placeholder="请输入岗位描述" />
          </Form.Item>
          <Form.Item label="任职要求" name="requirement" :span="2">
            <Input.TextArea :rows="3" placeholder="请输入任职要求" />
          </Form.Item>
        </div>
      </Form>
      <Descriptions v-else :column="2" bordered :items="[
        { key: 'jobCode', label: '岗位编码', children: current?.jobCode },
        { key: 'jobName', label: '岗位名称', children: current?.jobName },
        { key: 'deptName', label: '所属部门', children: current?.deptName },
        { key: 'headcount', label: '招聘人数', children: current?.headcount },
        { key: 'jobStatus', label: '状态', children: statusText(current?.jobStatus) },
        { key: 'jobDesc', label: '岗位描述', children: current?.jobDesc, span: 2 },
        { key: 'requirement', label: '任职要求', children: current?.requirement, span: 2 }
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
