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
const formState = reactive<Partial<JobPost>>({})
function resetFormState() {
  Object.keys(formState).forEach((k) => { delete (formState as any)[k] })
}

const statusOpts = [
  { label: '草稿', value: 0 },
  { label: '招聘中', value: 1 },
  { label: '已关闭', value: 2 },
  { label: '已完成', value: 3 }
]
const statusColor: Record<number, string> = { 0: 'default', 1: 'green', 2: 'orange', 3: 'blue' }
const eduOpts = [
  { label: '不限', value: 0 },
  { label: '大专', value: 1 },
  { label: '本科', value: 2 },
  { label: '硕士', value: 3 },
  { label: '博士', value: 4 }
]
const workYearOpts = [
  { label: '不限', value: 0 },
  { label: '应届', value: 1 },
  { label: '1-3年', value: 2 },
  { label: '3-5年', value: 3 },
  { label: '5-10年', value: 4 },
  { label: '10年以上', value: 5 }
]
const publicOpts = [
  { label: '不公开', value: 0 },
  { label: '公开', value: 1 }
]

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
function onCreate() { resetFormState(); mode.value = 'create'; current.value = null; visible.value = true }
function onView(r: JobPost) { mode.value = 'view'; current.value = r; visible.value = true }
function onEdit(r: JobPost) { resetFormState(); Object.assign(formState, r); mode.value = 'edit'; current.value = { ...r }; visible.value = true }

async function onSubmit() {
  try {
    await formRef.value.validate()
    const payload: any = { ...formState }
    if (payload.headCount === undefined || payload.headCount === null) payload.headCount = 0
    if (mode.value === 'create') {
      if (!payload.creatorEmpId) payload.creatorEmpId = 1
      await crud.save(payload); message.success('新增成功')
    } else if (mode.value === 'edit' && current.value?.id) {
      await crud.update(current.value.id, payload); message.success('更新成功')
    }
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
function statusText(s?: number) { return statusOpts.find(o => o.value === s)?.label ?? '-' }
function pubText(v?: number) { return v === 1 ? '公开' : '不公开' }

const columns = [
  { title: '岗位编码', dataIndex: 'jobCode', width: 120 },
  { title: '岗位名称', dataIndex: 'jobName', width: 180 },
  { title: '所属部门', dataIndex: 'deptName', width: 120 },
  { title: '工作城市', dataIndex: 'workCity', width: 100 },
  { title: '招聘人数', dataIndex: 'headCount', width: 100 },
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
        <Form.Item label="城市">
          <Input v-model:value="queryForm.workCity" placeholder="请输入" allow-clear style="width: 140px" />
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
        :scroll="{ x: 1100 }" row-key="id" @change="crud.handleTableChange" />
    </div>

    <Modal v-model:open="visible"
      :title="mode === 'create' ? '新增岗位' : mode === 'edit' ? '编辑岗位' : '岗位详情'"
      :footer="mode === 'view' ? null : undefined" width="720px" destroy-on-close>
      <Form v-if="mode !== 'view'" ref="formRef" layout="vertical" :model="formState">
        <a-row :gutter="16">
          <a-col :span="12"><Form.Item label="岗位编码" name="jobCode" :rules="[{ required: true, message: '请输入岗位编码' }]">
            <Input v-model:value="formState.jobCode" placeholder="如 JOB-001" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="岗位名称" name="jobName" :rules="[{ required: true, message: '请输入岗位名称' }]">
            <Input v-model:value="formState.jobName" placeholder="如 高级前端工程师" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="所属部门" name="deptName" :rules="[{ required: true, message: '请输入所属部门' }]">
            <Input v-model:value="formState.deptName" placeholder="请输入部门" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="工作城市" name="workCity" :rules="[{ required: true, message: '请输入工作城市' }]">
            <Input v-model:value="formState.workCity" placeholder="如 北京" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="详细地址" name="workAddress">
            <Input v-model:value="formState.workAddress" placeholder="请输入详细工作地址" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="招聘人数" name="headCount" :rules="[{ required: true, message: '请输入招聘人数' }]">
            <InputNumber v-model:value="formState.headCount" :min="0" style="width: 100%" placeholder="请输入招聘人数" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="薪资下限" name="salaryMin">
            <InputNumber v-model:value="formState.salaryMin" :min="0" :precision="2" style="width: 100%" placeholder="月薪下限(元)" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="薪资上限" name="salaryMax">
            <InputNumber v-model:value="formState.salaryMax" :min="0" :precision="2" style="width: 100%" placeholder="月薪上限(元)" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="学历要求" name="educationReq">
            <Select v-model:value="formState.educationReq" :options="eduOpts" placeholder="请选择" allow-clear />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="工作年限" name="workYearReq">
            <Select v-model:value="formState.workYearReq" :options="workYearOpts" placeholder="请选择" allow-clear />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="岗位状态" name="jobStatus">
            <Select v-model:value="formState.jobStatus" :options="statusOpts" placeholder="请选择" allow-clear />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="是否公开" name="isPublic">
            <Select v-model:value="formState.isPublic" :options="publicOpts" placeholder="请选择" allow-clear />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="发布时间" name="publishTime">
            <Input v-model:value="formState.publishTime" placeholder="YYYY-MM-DD HH:mm:ss" />
          </Form.Item></a-col>
          <a-col :span="12"><Form.Item label="截止关闭时间" name="closeTime">
            <Input v-model:value="formState.closeTime" placeholder="YYYY-MM-DD HH:mm:ss" />
          </Form.Item></a-col>
          <a-col :span="24"><Form.Item label="岗位职责" name="jobDuty" :rules="[{ required: true, message: '请输入岗位职责' }]">
            <Input.TextArea v-model:value="formState.jobDuty" :rows="3" placeholder="请输入岗位职责描述" />
          </Form.Item></a-col>
          <a-col :span="24"><Form.Item label="任职要求" name="jobRequirement" :rules="[{ required: true, message: '请输入任职要求' }]">
            <Input.TextArea v-model:value="formState.jobRequirement" :rows="3" placeholder="请输入岗位任职要求" />
          </Form.Item></a-col>
        </a-row>
      </Form>
      <Descriptions v-else :column="2" bordered>
        <Descriptions.Item label="岗位编码">{{ current?.jobCode || '-' }}</Descriptions.Item>
        <Descriptions.Item label="岗位名称">{{ current?.jobName || '-' }}</Descriptions.Item>
        <Descriptions.Item label="所属部门">{{ current?.deptName || '-' }}</Descriptions.Item>
        <Descriptions.Item label="工作城市">{{ current?.workCity || '-' }}</Descriptions.Item>
        <Descriptions.Item label="详细地址" :span="2">{{ current?.workAddress || '-' }}</Descriptions.Item>
        <Descriptions.Item label="薪资下限">{{ current?.salaryMin ?? '-' }}</Descriptions.Item>
        <Descriptions.Item label="薪资上限">{{ current?.salaryMax ?? '-' }}</Descriptions.Item>
        <Descriptions.Item label="招聘人数">{{ current?.headCount ?? '-' }}</Descriptions.Item>
        <Descriptions.Item label="状态">{{ statusText(current?.jobStatus) }}</Descriptions.Item>
        <Descriptions.Item label="是否公开">{{ pubText(current?.isPublic) }}</Descriptions.Item>
        <Descriptions.Item label="发布时间">{{ current?.publishTime || '-' }}</Descriptions.Item>
        <Descriptions.Item label="截止时间">{{ current?.closeTime || '-' }}</Descriptions.Item>
        <Descriptions.Item label="岗位职责" :span="2">{{ current?.jobDuty || '-' }}</Descriptions.Item>
        <Descriptions.Item label="任职要求" :span="2">{{ current?.jobRequirement || '-' }}</Descriptions.Item>
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
