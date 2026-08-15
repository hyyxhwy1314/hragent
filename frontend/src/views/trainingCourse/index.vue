<script setup lang="ts">
import { ref, computed, h, reactive } from 'vue'
import {
  Table, Button, Space, Input, InputNumber, Select, Form, Modal, Tag, message, App as AntApp, Descriptions
} from 'ant-design-vue'
import { PlusOutlined, SearchOutlined, ReloadOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { trainingCourseApi, type TrainingCourse } from '@/api/modules/trainingCourse'
import { useCrud } from '@/composables/useCrud'

defineOptions({ name: 'TrainingCoursePage' })
const { modal } = AntApp.useApp()

const formRef = ref()
const queryForm = ref<Record<string, any>>({})
const mode = ref<'view' | 'edit' | 'create'>('create')
const visible = ref(false)
const current = ref<TrainingCourse | null>(null)

const statusOpts = [
  { label: '未开始', value: 0 },
  { label: '进行中', value: 1 },
  { label: '已结束', value: 2 },
  { label: '已取消', value: 3 }
]
const statusColor: Record<number, string> = { 0: 'default', 1: 'blue', 2: 'green', 3: 'red' }

const crud = reactive(useCrud<TrainingCourse>(trainingCourseApi))
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
function onView(r: TrainingCourse) { mode.value = 'view'; current.value = r; visible.value = true }
function onEdit(r: TrainingCourse) { mode.value = 'edit'; current.value = { ...r }; visible.value = true }
async function onSubmit() {
  try {
    const vals = await formRef.value.validate()
    if (mode.value === 'create') { await crud.save(vals); message.success('新增成功') }
    else if (mode.value === 'edit' && current.value?.id) { await crud.update(current.value.id, vals); message.success('更新成功') }
    visible.value = false
  } catch { /* validation */ }
}
function onDelete(r: TrainingCourse) {
  if (!r.id) return
  modal.confirm({ title: '确认删除', content: `确定删除课程「${r.courseName}」吗？`,
    okText: '删除', okType: 'danger', cancelText: '取消',
    onOk: async () => { await crud.remove(r.id!); message.success('删除成功') } })
}
function statusText(s?: number) { return statusOpts.find(o => o.value === s)?.label ?? '-' }

const columns = [
  { title: '课程名称', dataIndex: 'courseName', width: 180 },
  { title: '课程类型', dataIndex: 'courseType', width: 120 },
  { title: '讲师', dataIndex: 'trainer', width: 110 },
  { title: '开始日期', dataIndex: 'startDate', width: 120 },
  { title: '结束日期', dataIndex: 'endDate', width: 120 },
  { title: '时长(小时)', dataIndex: 'duration', width: 100 },
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
      <h2 class="page-title">培训课程</h2>
      <p class="page-subtitle">管理企业培训课程体系与排期</p>
    </div>

    <div class="search-card">
      <Form layout="inline" :model="queryForm" :label-col="{ style: { width: 72 } }">
        <Form.Item label="课程名称">
          <Input v-model:value="queryForm.courseName" placeholder="请输入" allow-clear style="width: 180px" />
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
          <strong>课程列表</strong>
          <span style="color: rgba(0,0,0,0.45); font-size: 13px">共 {{ crud.total }} 条</span>
        </div>
        <div class="table-toolbar-right">
          <Button type="primary" :icon="h(PlusOutlined)" @click="onCreate">新增课程</Button>
        </div>
      </div>

      <Table :columns="columns" :data-source="safeDataSource" :loading="crud.loading"
        :pagination="pagination"
        :scroll="{ x: 1000 }" row-key="id" @change="crud.handleTableChange" />
    </div>

    <Modal v-model:open="visible"
      :title="mode === 'create' ? '新增课程' : mode === 'edit' ? '编辑课程' : '课程详情'"
      :footer="mode === 'view' ? null : undefined" width="640px" destroy-on-close>
      <Form v-if="mode !== 'view'" ref="formRef" layout="vertical" :initial-values="mode === 'edit' ? current : {}">
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px">
          <Form.Item label="课程名称" name="courseName" :rules="[{ required: true, message: '请输入课程名称' }]">
            <Input placeholder="如 Vue3 实战" />
          </Form.Item>
          <Form.Item label="课程类型" name="courseType">
            <Input placeholder="如 技术培训/软技能" />
          </Form.Item>
          <Form.Item label="讲师" name="trainer"><Input placeholder="请输入讲师" /></Form.Item>
          <Form.Item label="时长(小时)" name="duration"><InputNumber :min="0" style="width: 100%" /></Form.Item>
          <Form.Item label="开始日期" name="startDate"><Input placeholder="YYYY-MM-DD" /></Form.Item>
          <Form.Item label="结束日期" name="endDate"><Input placeholder="YYYY-MM-DD" /></Form.Item>
          <Form.Item label="状态" name="status"><Select :options="statusOpts" placeholder="请选择" /></Form.Item>
          <Form.Item label="课程描述" name="description" :span="2">
            <Input.TextArea :rows="3" placeholder="请输入课程描述" />
          </Form.Item>
        </div>
      </Form>
      <Descriptions v-else :column="2" bordered :items="[
        { key: 'courseName', label: '课程名称', children: current?.courseName },
        { key: 'courseType', label: '课程类型', children: current?.courseType },
        { key: 'trainer', label: '讲师', children: current?.trainer },
        { key: 'duration', label: '时长', children: current?.duration },
        { key: 'startDate', label: '开始日期', children: current?.startDate },
        { key: 'endDate', label: '结束日期', children: current?.endDate },
        { key: 'status', label: '状态', children: statusText(current?.status) },
        { key: 'description', label: '课程描述', children: current?.description, span: 2 }
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
