<script setup lang="ts">
import { ref, computed, h, reactive } from 'vue'
import {
  Table, Button, Space, Input, Select, Form, Tag, Upload, message, App as AntApp, Modal
} from 'ant-design-vue'
import {
  SearchOutlined, ReloadOutlined, FileTextOutlined, UploadOutlined, RobotOutlined
} from '@ant-design/icons-vue'
import { resumeApi, type Resume, type ResumeDeepAnalysisVO } from '@/api/modules/resume'
import { useCrud } from '@/composables/useCrud'

defineOptions({ name: 'ResumePage' })
const { modal } = AntApp.useApp()

const queryForm = ref<Record<string, any>>({})
const uploading = ref(false)
// 深度分析
const deepAnalyzing = ref(false)
const deepAnalyzingId = ref<number | null>(null)
const deepAnalysisResult = ref<ResumeDeepAnalysisVO | null>(null)
const showDeepModal = ref(false)
const deepModalTitle = ref('深度 AI 简历评估')

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

// 上传简历：选文件后自动上传文件 + 创建简历记录
async function onUploadFile(options: any) {
  const { file, onSuccess, onError } = options
  uploading.value = true
  try {
    const vo = await resumeApi.upload(file)
    const baseName = file.name.replace(/\.[^.]+$/, '')
    await crud.save({
      resumeName: baseName,
      resumeFileId: vo.fileId,
      resumeContent: '', // 不再自动填充简历内容
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

// AI深度分析简历
async function onDeepAnalyze(r: Resume) {
  if (!r.id || !r.resumeFileId) {
    message.warning('该简历未上传附件')
    return
  }
  deepModalTitle.value = '深度 AI 简历评估'
  deepAnalyzing.value = true
  deepAnalyzingId.value = r.id
  try {
    const result = await resumeApi.deepAnalyze(r.id)
    deepAnalysisResult.value = result
    showDeepModal.value = true
    if (result.success) {
      message.success('深度分析完成')
    } else {
      message.warning('深度分析失败: ' + result.summary)
    }
  } catch {
    /* 错误提示已由拦截器统一处理 */
  } finally {
    deepAnalyzing.value = false
    deepAnalyzingId.value = null
  }
}

// 查看最近一次评分（读取落库结果，不重新分析）
async function onViewScore(r: Resume) {
  if (!r.id) { message.warning('简历ID无效'); return }
  try {
    const result = await resumeApi.getScore(r.id)
    if (result.success) {
      deepModalTitle.value = '简历评分详情（上次深度分析结果）'
      deepAnalysisResult.value = result
      showDeepModal.value = true
    } else {
      message.info('该简历尚未进行深度 AI 分析，请先点击"深度AI分析"')
    }
  } catch { /* 错误提示已由拦截器统一处理 */ }
}

// 综合评分颜色与评级
function scoreColor(s?: number) {
  if (s == null) return '#8c8c8c'
  if (s >= 85) return '#52c41a'
  if (s >= 70) return '#1890ff'
  if (s >= 60) return '#faad14'
  return '#ff4d4f'
}
function scoreLevel(s?: number) {
  if (s == null) return '暂无'
  if (s >= 85) return '推荐'
  if (s >= 70) return '良好'
  if (s >= 60) return '一般'
  return '待观察'
}
function clampScore(s?: number) {
  if (s == null) return 0
  return Math.max(0, Math.min(100, s))
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
    title: '操作', key: 'action', width: 320, fixed: 'right',
    customRender: ({ record }: any) => h(Space, { size: 0, wrap: true }, () => [
      h(Button, { size: 'small', type: 'link', disabled: !record.resumeFileId, onClick: () => onPreview(record) }, () => '预览'),
      h(Button, { size: 'small', type: 'link', disabled: !record.resumeFileId, onClick: () => onDownload(record) }, () => '下载'),
      h(Button, { 
        size: 'small', 
        type: 'link', 
        icon: h(RobotOutlined), 
        disabled: !record.resumeFileId, 
        loading: deepAnalyzing.value && deepAnalyzingId.value === record.id,
        onClick: () => onDeepAnalyze(record)
      }, () => '深度AI分析'),
      h(Button, { 
        size: 'small', 
        type: 'link', 
        disabled: !record.resumeFileId, 
        onClick: () => onViewScore(record)
      }, () => '评分'),
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

    <!-- 深度分析结果弹窗 -->
    <Modal
      v-model:open="showDeepModal"
      :title="deepModalTitle"
      :footer="null"
      width="860px"
    >
      <a-spin :spinning="deepAnalyzing">
        <div v-if="deepAnalysisResult">
          <a-alert
            v-if="!deepAnalysisResult.success"
            type="error"
            show-icon
            :message="deepAnalysisResult.summary || '深度分析失败'"
            style="margin-bottom: 16px"
          />
          <template v-else>
            <!-- 顶部评分卡片 -->
            <div class="score-head">
              <a-progress
                type="circle"
                :percent="clampScore(deepAnalysisResult.score)"
                :size="112"
                :stroke-width="10"
                :stroke-color="scoreColor(deepAnalysisResult.score)"
                :format="() => String(deepAnalysisResult.score ?? '--')"
              />
              <div class="score-head-info">
                <div class="score-level">
                  综合匹配度
                  <span class="score-badge" :style="{ color: scoreColor(deepAnalysisResult.score), background: scoreColor(deepAnalysisResult.score) + '1a' }">
                    {{ scoreLevel(deepAnalysisResult.score) }}
                  </span>
                </div>
                <div class="score-tip">基于系统技能标签库，对候选人的技能覆盖度、经验与综合胜任力进行量化评估</div>
              </div>
            </div>

            <!-- 技能标签匹配 -->
            <div class="deep-section" v-if="deepAnalysisResult.tagMatch?.length">
              <div class="deep-title blue">
                <span class="dot" style="background:#4a7fc1"></span>技能标签匹配
              </div>
              <div class="tag-match" v-for="(t, i) in deepAnalysisResult.tagMatch" :key="i">
                <div class="tag-match-row">
                  <span class="tag-match-cat">{{ t.category }}</span>
                  <a-progress
                    class="tag-match-bar"
                    :percent="clampScore(t.score)"
                    :show-info="false"
                    :stroke-color="scoreColor(t.score)"
                  />
                  <span class="tag-match-score">{{ t.score ?? '--' }}</span>
                </div>
                <div class="tag-match-tags">
                  <span v-for="(m, j) in t.matched" :key="'m'+j" class="tag-chip hit">{{ m }}</span>
                  <span v-if="t.missing?.length" class="tag-sep">缺失</span>
                  <span v-for="(x, j) in t.missing" :key="'x'+j" class="tag-chip miss">{{ x }}</span>
                </div>
              </div>
            </div>

            <div class="deep-summary">
              {{ deepAnalysisResult.summary || '暂无总体评价' }}
            </div>

            <div class="deep-section" v-if="deepAnalysisResult.strengths?.length">
              <div class="deep-title green">
                <span class="dot" style="background:#1e6f4e"></span>核心优势
              </div>
              <ul class="deep-list">
                <li v-for="(s, i) in deepAnalysisResult.strengths" :key="i">{{ s }}</li>
              </ul>
            </div>

            <div class="deep-section" v-if="deepAnalysisResult.weaknesses?.length">
              <div class="deep-title orange">
                <span class="dot" style="background:#b45309"></span>潜在短板
              </div>
              <ul class="deep-list">
                <li v-for="(w, i) in deepAnalysisResult.weaknesses" :key="i">{{ w }}</li>
              </ul>
            </div>

            <div class="deep-section" v-if="deepAnalysisResult.matchAnalysis">
              <div class="deep-title blue">
                <span class="dot" style="background:#4a7fc1"></span>岗位匹配分析
              </div>
              <div class="deep-text">{{ deepAnalysisResult.matchAnalysis }}</div>
            </div>

            <div class="deep-section" v-if="deepAnalysisResult.suggestions?.length">
              <div class="deep-title blue">
                <span class="dot" style="background:#4a7fc1"></span>招聘 / 面试建议
              </div>
              <ul class="deep-list">
                <li v-for="(s, i) in deepAnalysisResult.suggestions" :key="i">{{ s }}</li>
              </ul>
            </div>
          </template>
        </div>
      </a-spin>
    </Modal>
  </div>
</template>

<style scoped>
.score-head {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 20px;
  border-radius: 12px;
  background: linear-gradient(135deg, #f6f9ff, #ffffff);
  border: 1px solid #eef2f7;
  margin-bottom: 8px;
}
.score-head-info {
  flex: 1;
  min-width: 0;
}
.score-level {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  display: flex;
  align-items: center;
  gap: 10px;
}
.score-badge {
  font-size: 12px;
  font-weight: 600;
  padding: 2px 10px;
  border-radius: 20px;
}
.score-tip {
  margin-top: 8px;
  font-size: 13px;
  color: #6b7280;
  line-height: 1.6;
}
.deep-summary {
  font-size: 14px;
  line-height: 1.7;
  color: #1f2937;
  background: #f0f7ff;
  border-radius: 8px;
  padding: 12px 14px;
  white-space: pre-wrap;
  margin-top: 12px;
}
.deep-section {
  margin-top: 18px;
}
.deep-title {
  font-weight: 600;
  font-size: 13px;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.deep-title.green { color: #1e6f4e; }
.deep-title.orange { color: #b45309; }
.deep-title.blue { color: #4a7fc1; }
.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  display: inline-block;
}
.deep-list {
  margin: 0;
  padding-left: 18px;
  color: #374151;
  font-size: 13px;
  line-height: 1.8;
}
.deep-text {
  font-size: 13px;
  line-height: 1.7;
  color: #374151;
}
.tag-match {
  padding: 12px 14px;
  border: 1px solid #eef2f7;
  border-radius: 8px;
  margin-bottom: 10px;
  background: #fbfcfe;
}
.tag-match-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.tag-match-cat {
  width: 90px;
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 600;
  color: #374151;
}
.tag-match-bar {
  flex: 1;
  margin: 0;
}
.tag-match-score {
  width: 34px;
  text-align: right;
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}
.tag-match-tags {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.tag-chip {
  font-size: 12px;
  padding: 1px 10px;
  border-radius: 20px;
}
.tag-chip.hit {
  color: #1e6f4e;
  background: #e7f6ee;
}
.tag-chip.miss {
  color: #b45309;
  background: #fdf3e3;
}
.tag-sep {
  font-size: 12px;
  color: #9ca3af;
  margin: 0 2px;
}
</style>
