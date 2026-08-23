<script setup lang="ts">
import { ref, computed, h, reactive } from 'vue'
import {
  Table, Button, Space, Input, Select, Form, Tag, Upload, message, App as AntApp
} from 'ant-design-vue'
import {
  SearchOutlined, ReloadOutlined, FileTextOutlined, UploadOutlined
} from '@ant-design/icons-vue'
import { resumeApi, type Resume } from '@/api/modules/resume'
import { useCrud } from '@/composables/useCrud'

defineOptions({ name: 'ResumePage' })
const { modal } = AntApp.useApp()

const queryForm = ref<Record<string, any>>({})
const uploading = ref(false)

const statusOpts = [
  { label: '待筛选', value: 0 },
  { label: '已通过', value: 1 },
  { label: '已淘汰', value: 2 },
  { label: '面试中', value: 3 },
  { label: '归档', value: 4 }
]
const statusColor: Record<number, string> = { 0: 'default', 1: 'green', 2: 'red', 3: 'blue', 4: 'orange' }

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

// 上传简历：选文件后自动上传文件 + 创建简历记录（OCR 原文随记录落库，供后续 AI 分析）
async function onUploadFile(options: any) {
  const { file, onSuccess, onError } = options
  uploading.value = true
  try {
    const vo = await resumeApi.upload(file)
    const baseName = file.name.replace(/\.[^.]+$/, '')
    await crud.save({
      resumeName: baseName,
      resumeFileId: vo.fileId,
      resumeContent: vo.parsed?.rawText || '',
      resumeStatus: 0
    })
    message.success('简历上传成功')
    await crud.fetch()
    onSuccess?.(vo)
  } catch (e) {
    onError?.(e)
  } finally {
    uploading.value = false
  }
}

function beforeUpload(file: File) {
  const limit = 50 * 1024 * 1024
  if (file.size > limit) { message.error('文件不能超过 50MB'); return false }
  return true
}

function onDelete(r: Resume) {
  if (!r.id) return
  modal.confirm({ title: '确认删除', content: `确定删除简历「${r.resumeName}」吗？`,
    okText: '删除', okType: 'danger', cancelText: '取消',
    onOk: async () => { await crud.remove(r.id!); message.success('删除成功') } })
}
function onArchive(r: Resume) {
  if (!r.id) return
  modal.confirm({ title: '确认归档', content: `确定归档简历「${r.resumeName}」吗？`,
    okText: '归档', cancelText: '取消',
    onOk: async () => { await resumeApi.archive(r.id!); message.success('归档成功'); await crud.fetch() } })
}
async function onDownload(r: Resume) {
  if (!r.id || !r.resumeFileId) { message.warning('该简历未上传附件'); return }
  try { await resumeApi.downloadFile(r.id) } catch { /* handled */ }
}
async function onPreview(r: Resume) {
  if (!r.id || !r.resumeFileId) { message.warning('该简历未上传附件'); return }
  try {
    const res = await resumeApi.previewFile(r.id)
    if (res?.previewUrl) window.open(res.previewUrl, '_blank')
  } catch { /* handled */ }
}
function statusText(s?: number) {
  return statusOpts.find(o => o.value === s)?.label ?? '-'
}
function formatTime(s?: string) {
  if (!s) return '-'
  return s.substring(0, 19).replace('T', ' ')
}

const columns: any[] = [
  { title: '简历名称', dataIndex: 'resumeName', width: 220, ellipsis: true },
  {
    title: '附件', dataIndex: 'resumeFileId', width: 80,
    customRender: ({ record }: any) => record.resumeFileId
      ? h(Tag, { color: 'blue' }, () => h(FileTextOutlined))
      : h('span', { style: { color: 'rgba(0,0,0,0.35)' } }, '无')
  },
  {
    title: '状态', dataIndex: 'resumeStatus', width: 120,
    customRender: ({ record }: any) => h(Select, {
      size: 'small',
      value: record.resumeStatus,
      options: statusOpts,
      style: 'width: 96px',
      onChange: async (val: number) => {
        try {
          await crud.update(record.id, { resumeStatus: val })
          message.success(`状态已改为「${statusOpts.find(o => o.value === val)?.label}」`)
        } catch { /* handled */ }
      }
    })
  },
  {
    title: '创建时间', dataIndex: 'createTime', width: 180,
    customRender: ({ record }: any) => formatTime(record.createTime)
  },
  {
    title: '操作', key: 'action', width: 220, fixed: 'right',
    customRender: ({ record }: any) => h(Space, { size: 0, wrap: true }, () => [
      h(Button, { size: 'small', type: 'link', disabled: !record.resumeFileId, onClick: () => onPreview(record) }, () => '预览'),
      h(Button, { size: 'small', type: 'link', disabled: !record.resumeFileId, onClick: () => onDownload(record) }, () => '下载'),
      h(Button, { size: 'small', type: 'link', danger: true, onClick: () => onArchive(record) }, () => '归档'),
      h(Button, { size: 'small', type: 'link', danger: true, onClick: () => onDelete(record) }, () => '删除')
    ])
  }
]
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">简历管理</h2>
      <p class="page-subtitle">上传候选人简历，支持在线预览与下载，OCR 原文已留存供后续 AI 分析</p>
    </div>

    <div class="search-card">
      <Form layout="inline" :model="queryForm" :label-col="{ style: { width: 80 } }">
        <Form.Item label="简历名称">
          <Input v-model:value="queryForm.resumeName" placeholder="请输入" allow-clear style="width: 180px" />
        </Form.Item>
        <Form.Item label="状态">
          <Select v-model:value="queryForm.resumeStatus" placeholder="全部" allow-clear style="width: 130px" :options="statusOpts" />
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
          <Upload
            :show-upload-list="false"
            :before-upload="beforeUpload"
            :custom-request="onUploadFile"
            accept=".pdf,.doc,.docx,.txt,.html,.png,.jpg,.jpeg"
            :disabled="uploading"
          >
            <Button type="primary" :icon="h(UploadOutlined)" :loading="uploading">上传简历</Button>
          </Upload>
        </div>
      </div>

      <Table :columns="columns" :data-source="safeDataSource" :loading="crud.loading"
        :pagination="pagination"
        :scroll="{ x: 900 }" row-key="id" @change="crud.handleTableChange" />
    </div>
  </div>
</template>
