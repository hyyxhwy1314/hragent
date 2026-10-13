<script setup lang="ts">
import { ref, reactive, h, onMounted } from 'vue'
import { Row, Col, Card, Form, Input, Select, DatePicker, Button, Table, Tag, message, Empty, Alert } from 'ant-design-vue'
import { ReloadOutlined, SafetyOutlined } from '@ant-design/icons-vue'
import dayjs, { type Dayjs } from 'dayjs'
import {
  applyLeave, getLeaveBalance, getLeaveRecords,
  applyCertificate, getCertificateRecords, getReminders,
  type LeaveBalance, type LeaveRequestRecord, type CertificateRequestRecord, type ExpireReminder
} from '@/api/modules/selfService'
import { FLOW_STATUS_MAP } from '@/api/modules/flow'

defineOptions({ name: 'SelfServicePage' })

const loading = ref(false)
const balance = ref<LeaveBalance | null>(null)
const leaveRecords = ref<LeaveRequestRecord[]>([])
const certRecords = ref<CertificateRequestRecord[]>([])
const reminders = ref<ExpireReminder[]>([])

const leaveTypeOptions = ['年假', '事假', '病假', '调休', '其他']
const certTypeOptions = ['在职证明', '收入证明']

// 请假表单
const leaveForm = reactive({
  leaveType: '年假',
  range: undefined as [Dayjs, Dayjs] | undefined,
  reason: ''
})
const leaveSubmitting = ref(false)

// 证明表单
const certForm = reactive({
  certType: '在职证明',
  purpose: ''
})
const certSubmitting = ref(false)

function statusTag(status?: number, text?: string) {
  if (!status) return h(Tag, { color: 'default' }, () => text || '审批中')
  const m = FLOW_STATUS_MAP[status]
  return h(Tag, { color: m?.color || 'default' }, () => text || m?.label || '审批中')
}

async function refreshAll() {
  loading.value = true
  try {
    const [b, lr, cr, rm] = await Promise.all([
      getLeaveBalance(), getLeaveRecords(), getCertificateRecords(), getReminders()
    ])
    balance.value = b
    leaveRecords.value = lr || []
    certRecords.value = cr || []
    reminders.value = rm || []
  } catch {
    // 错误提示由请求拦截器统一处理
  } finally {
    loading.value = false
  }
}

onMounted(refreshAll)

async function submitLeave() {
  if (!leaveForm.range || leaveForm.range.length !== 2) {
    message.warning('请选择请假起止日期')
    return
  }
  leaveSubmitting.value = true
  try {
    const result = await applyLeave({
      leaveType: leaveForm.leaveType,
      startDate: leaveForm.range[0].format('YYYY-MM-DD'),
      endDate: leaveForm.range[1].format('YYYY-MM-DD'),
      reason: leaveForm.reason || undefined
    })
    message.success(`请假申请已提交，流程单号 ${result.flowNo}`)
    if (result.balanceTip) message.warning(result.balanceTip)
    leaveForm.range = undefined
    leaveForm.reason = ''
    refreshAll()
  } catch {
    // 错误提示由请求拦截器统一处理
  } finally {
    leaveSubmitting.value = false
  }
}

async function submitCert() {
  certSubmitting.value = true
  try {
    const result = await applyCertificate({
      certType: certForm.certType,
      purpose: certForm.purpose || undefined
    })
    message.success(`${certForm.certType}申请已提交，流程单号 ${result.flowNo}`)
    certForm.purpose = ''
    refreshAll()
  } catch {
    // 错误提示由请求拦截器统一处理
  } finally {
    certSubmitting.value = false
  }
}

const leaveColumns: any[] = [
  { title: '类型', dataIndex: 'leaveType', width: 90 },
  {
    title: '起止日期', key: 'range', width: 190,
    customRender: ({ record }: any) => `${record.startDate} ~ ${record.endDate}`
  },
  { title: '天数', dataIndex: 'leaveDays', width: 70 },
  { title: '原因', dataIndex: 'reason', ellipsis: true },
  { title: '流程单号', dataIndex: 'flowNo', width: 190 },
  {
    title: '状态', key: 'status', width: 90,
    customRender: ({ record }: any) => statusTag(record.flowStatus, record.statusText)
  },
  { title: '提交时间', dataIndex: 'createTime', width: 170 }
]

const certColumns: any[] = [
  { title: '证明类型', dataIndex: 'certType', width: 110 },
  { title: '用途', dataIndex: 'purpose', ellipsis: true },
  { title: '流程单号', dataIndex: 'flowNo', width: 190 },
  {
    title: '状态', key: 'status', width: 90,
    customRender: ({ record }: any) => statusTag(record.flowStatus, record.statusText)
  },
  { title: '提交时间', dataIndex: 'createTime', width: 170 }
]
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">员工自助</h2>
        <p class="page-subtitle">请假申请、假期余额、证明开具与到期提醒</p>
      </div>
      <div class="page-header-right">
        <Button :icon="h(ReloadOutlined)" :loading="loading" @click="refreshAll">刷新</Button>
      </div>
    </div>

    <!-- 假期余额概览 -->
    <Row :gutter="16" style="margin-bottom: 16px">
      <Col :span="8">
        <Card :bordered="false" class="stat-card">
          <div class="stat-label">年度假期总额</div>
          <div class="stat-value">{{ balance?.totalDays ?? '-' }}<span class="stat-unit"> 天</span></div>
        </Card>
      </Col>
      <Col :span="8">
        <Card :bordered="false" class="stat-card">
          <div class="stat-label">已使用</div>
          <div class="stat-value stat-muted">{{ balance?.usedDays ?? '-' }}<span class="stat-unit"> 天</span></div>
        </Card>
      </Col>
      <Col :span="8">
        <Card :bordered="false" class="stat-card stat-card-accent">
          <div class="stat-label">剩余可休</div>
          <div class="stat-value">{{ balance?.remainingDays ?? '-' }}<span class="stat-unit"> 天</span></div>
        </Card>
      </Col>
    </Row>

    <!-- 申请表单 -->
    <Row :gutter="16" style="margin-bottom: 16px">
      <Col :span="12">
        <Card :bordered="false" class="form-card">
          <div class="form-card-title">请假申请</div>
          <Form layout="vertical">
            <Form.Item label="请假类型">
              <Select v-model:value="leaveForm.leaveType" :options="leaveTypeOptions.map(v => ({ label: v, value: v }))" />
            </Form.Item>
            <Form.Item label="起止日期">
              <DatePicker.RangePicker
                v-model:value="leaveForm.range"
                style="width: 100%"
                :allow-clear="false"
                :disabled-date="(d: Dayjs) => !!d && d.isBefore(dayjs().startOf('day'))"
              />
            </Form.Item>
            <Form.Item label="请假原因">
              <Input.TextArea v-model:value="leaveForm.reason" :rows="3" placeholder="请输入请假原因（选填）" :maxlength="200" show-count />
            </Form.Item>
            <Form.Item>
              <Button type="primary" :loading="leaveSubmitting" block @click="submitLeave">提交请假申请</Button>
            </Form.Item>
          </Form>
        </Card>
      </Col>
      <Col :span="12">
        <Card :bordered="false" class="form-card">
          <div class="form-card-title">证明开具</div>
          <Form layout="vertical">
            <Form.Item label="证明类型">
              <Select v-model:value="certForm.certType" :options="certTypeOptions.map(v => ({ label: v, value: v }))" />
            </Form.Item>
            <Form.Item label="用途说明">
              <Input.TextArea v-model:value="certForm.purpose" :rows="3" placeholder="如：办理贷款、签证、购房等（选填）" :maxlength="200" show-count />
            </Form.Item>
            <Form.Item>
              <Button type="primary" :loading="certSubmitting" block @click="submitCert">提交证明申请</Button>
            </Form.Item>
            <div class="form-tip">
              <SafetyOutlined /> 审批通过后证明内容将以邮件发送至您的邮箱
            </div>
          </Form>
        </Card>
      </Col>
    </Row>

    <!-- 请假记录 -->
    <div class="table-card" style="margin-bottom: 16px">
      <div class="table-toolbar">
        <div class="table-toolbar-left">
          <strong>我的请假记录</strong>
          <span style="color: rgba(0,0,0,0.45); font-size: 13px">{{ leaveRecords.length }} 条</span>
        </div>
      </div>
      <Table :columns="leaveColumns" :data-source="leaveRecords" :loading="loading"
        :pagination="false" :scroll="{ x: 960 }" row-key="id">
        <template #emptyText><Empty description="暂无请假记录" :image="Empty.PRESENTED_IMAGE_SIMPLE" /></template>
      </Table>
    </div>

    <!-- 证明记录 -->
    <div class="table-card" style="margin-bottom: 16px">
      <div class="table-toolbar">
        <div class="table-toolbar-left">
          <strong>我的证明记录</strong>
          <span style="color: rgba(0,0,0,0.45); font-size: 13px">{{ certRecords.length }} 条</span>
        </div>
      </div>
      <Table :columns="certColumns" :data-source="certRecords" :loading="loading"
        :pagination="false" :scroll="{ x: 720 }" row-key="id">
        <template #emptyText><Empty description="暂无证明记录" :image="Empty.PRESENTED_IMAGE_SIMPLE" /></template>
      </Table>
    </div>

    <!-- 到期提醒 -->
    <Card :bordered="false" class="table-card" style="margin-bottom: 16px">
      <div class="table-toolbar">
        <div class="table-toolbar-left">
          <strong>到期提醒</strong>
          <span style="color: rgba(0,0,0,0.45); font-size: 13px">合同到期 / 转正到期</span>
        </div>
      </div>
      <div v-if="reminders.length" class="reminder-list">
        <div v-for="r in reminders" :key="r.id" class="reminder-item">
          <Tag :color="r.remindType === 'CONTRACT' ? 'blue' : 'gold'">{{ r.remindTypeText }}</Tag>
          <span class="reminder-text">到期日期：{{ r.expireDate }}（提醒日期 {{ r.remindDate }}）</span>
          <span class="reminder-sent">{{ r.sent === 1 ? '已提醒' : '待提醒' }}</span>
        </div>
      </div>
      <Alert v-else type="info" show-icon message="暂无到期提醒" description="系统每日自动扫描合同与转正到期，临近到期时通过邮件提醒" style="margin: 4px 0 8px" />
    </Card>
  </div>
</template>

<style scoped>
.stat-card {
  border-radius: var(--radius-lg);
  border: 1px solid var(--border);
  box-shadow: var(--shadow-sm);
}
.stat-card :deep(.ant-card-body) {
  padding: 18px 20px;
}
.stat-label {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-bottom: 8px;
}
.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.2;
}
.stat-value .stat-unit {
  font-size: 13px;
  font-weight: 400;
  color: var(--text-tertiary);
}
.stat-muted .stat-value {
  color: var(--text-tertiary);
}
.stat-card-accent {
  background: var(--accent-light);
  border-color: transparent;
}
.stat-card-accent .stat-value {
  color: var(--accent);
}

.form-card {
  border-radius: var(--radius-lg);
  border: 1px solid var(--border);
  box-shadow: var(--shadow-sm);
}
.form-card :deep(.ant-card-body) {
  padding: 20px 24px;
}
.form-card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-light);
}
.form-tip {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 4px;
}

.reminder-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.reminder-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  transition: background 0.15s;
}
.reminder-item:hover {
  background: var(--bg-tertiary);
}
.reminder-text {
  flex: 1;
  color: var(--text-secondary);
}
.reminder-sent {
  font-size: 12px;
  color: var(--text-tertiary);
}
</style>
