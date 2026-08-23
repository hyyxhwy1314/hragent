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
const formState = reactive<Partial<TrainingCourse>>({})
function resetFormState() {
  Object.keys(formState).forEach((k) => { delete (formState as any)[k] })
}

const statusOpts = [
  { label: '禁用', value: 0 },
  { label: '启用', value: 1 }
]
const statusColor: Record<number, string> = { 0: 'default', 1: 'green' }

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
function onCreate() { resetFormState(); mode.value = 'create'; current.value = null; visible.value = true }
function onView(r: TrainingCourse) { mode.value = 'view'; current.value = r; visible.value = true }
function onEdit(r: TrainingCourse) { resetFormState(); Object.assign(formState, r); mode.value = 'edit'; current.value = { ...r }; visible.value = true }

async function onSubmit() {
  try {
    await formRef.value.validate()
    const payload: any = { ...formState }
    if (mode.value === 'create') { await crud.save(payload); message.success('新增成功') }
    else if (mode.value === 'edit' && current.value?.id) { await crud.update(current.value.id, payload); message.success('更新成功') }
    visible.value = false
  } catch { /* validation */ }
}
function onDelete(r: TrainingCourse) {
  if (!r.id) return
  modal.confirm({
    title: '确认删除', content: `确定删除课程「${r.courseName}」吗？`,
    okText: '删除', okType: 'danger', cancelText: '取消',
    onOk: async () => { await crud.remove(r.id!); message.success('删除成功') }
  })
}
function statusText(s?: number) { return statusOpts.find(o => o.value === s)?.label ?? '-' }

const columns: any[] = [
  { title: '课程编码', dataIndex: 'courseCode', width: 140 },
  { title: '课程名称', dataIndex: 'courseName', width: 180 },
  { title: '课程类型', dataIndex: 'courseType', width: 120 },
  { title: '时长(分钟)', dataIndex: 'durationMin', width: 110 },
  { title: '关联标签ID', dataIndex: 'tagIds', width: 160, ellipsis: true },
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
      <p class="page-subtitle">管理企业培训课程体系</p>
    </div>

    <div class="search-card">
      <Form layout="inline" :model="queryForm" :label-col="{ style: { width: 72 } }">
        <Form.Item label="课程编码">
          <Input v-model:value="queryForm.courseCode" placeholder="请输入" allow-clear style="width: 160px" />
        </Form.Item>
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
      <Form v-if="mode !== 'view'" ref="formRef" layout="vertical" :model="formState">
        <a-row :gutter="16">
          <a-col :span="12"><Form.Item label="课程编码" name="courseCode" :rules="[{ required: true, message: '请输入课程编码' }]">
            <Input v-model:value="formState.courseCode" placeholder="如 TC-001" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="课程名称" name="courseName" :rules="[{ required: true, message: '请输入课程名称' }]">
            <Input v-model:value="formState.courseName" placeholder="如 Vue3 实战" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="课程类型" name="courseType">
            <Input v-model:value="formState.courseType" placeholder="如 线上/线下" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="课程时长(分钟)" name="durationMin">
            <InputNumber v-model:value="formState.durationMin" :min="0" style="width: 100%" placeholder="如 120" />
          </Form.Item></a-col>
          <a-col :span="24"><Form.Item label="关联能力标签ID" name="tagIds">
            <Input v-model:value="formState.tagIds" placeholder="多个ID用逗号分隔，如 1,2,3" />
          </Form.Item></a-col>
          <a-col :span="24"><Form.Item label="课程状态" name="status">
            <Select v-model:value="formState.status" :options="statusOpts" placeholder="请选择" allow-clear />
          </Form.Item></a-col>
          <a-col :span="24"><Form.Item label="课程详细描述" name="courseDesc">
            <Input.TextArea v-model:value="formState.courseDesc" :rows="3" placeholder="请输入课程描述" />
          </Form.Item></a-col>
          <a-col :span="24"><Form.Item label="课程学习目标" name="courseTarget">
            <Input.TextArea v-model:value="formState.courseTarget" :rows="3" placeholder="请输入学习目标" />
          </Form.Item></a-col>
        </a-row>
      </Form>
      <Descriptions v-else :column="2" bordered>
        <Descriptions.Item label="课程编码">{{ current?.courseCode || '-' }}</Descriptions.Item>
        <Descriptions.Item label="课程名称">{{ current?.courseName || '-' }}</Descriptions.Item>
        <Descriptions.Item label="课程类型">{{ current?.courseType || '-' }}</Descriptions.Item>
        <Descriptions.Item label="时长(分钟)">{{ current?.durationMin ?? '-' }}</Descriptions.Item>
        <Descriptions.Item label="关联标签ID" :span="2">{{ current?.tagIds || '-' }}</Descriptions.Item>
        <Descriptions.Item label="状态">{{ statusText(current?.status) }}</Descriptions.Item>
        <Descriptions.Item label="课程描述" :span="2">{{ current?.courseDesc || '-' }}</Descriptions.Item>
        <Descriptions.Item label="学习目标" :span="2">{{ current?.courseTarget || '-' }}</Descriptions.Item>
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
