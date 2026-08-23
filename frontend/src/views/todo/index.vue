<script setup lang="ts">
import { ref, h, computed } from 'vue'
import { Table, Button, Space, Tag, Modal, Input, message, App as AntApp, Descriptions } from 'ant-design-vue'
import { ReloadOutlined, CheckOutlined, CloseOutlined } from '@ant-design/icons-vue'
import { listTodoTasks, completeTask, FLOW_TYPE_MAP, type TaskVO, type TaskCompleteDTO } from '@/api/modules/flow'
import { employeeApi, type Employee } from '@/api/modules/employee'

defineOptions({ name: 'TodoPage' })
const { modal } = AntApp.useApp()

const loading = ref(false)
const dataSource = ref<TaskVO[]>([])

// 员工列表用于姓名映射
const employees = ref<Employee[]>([])
const empMap = computed(() => {
  const m: Record<number, Employee> = {}
  employees.value.forEach(e => { if (e.id) m[e.id] = e })
  return m
})
function empName(id?: number) {
  if (!id) return '-'
  const e = empMap.value[id]
  return e ? (e.empName || e.empNo || `#${id}`) : `#${id}`
}
function typeText(t: string) { return FLOW_TYPE_MAP[t] || t }

async function fetchTodo() {
  loading.value = true
  try {
    dataSource.value = await listTodoTasks() || []
  } catch {
    dataSource.value = []
  } finally {
    loading.value = false
  }
}

async function loadEmployees() {
  try { employees.value = await employeeApi.list() || [] } catch { employees.value = [] }
}

Promise.all([fetchTodo(), loadEmployees()])

// 审批弹窗
const approveVisible = ref(false)
const currentTask = ref<TaskVO | null>(null)
const approveResult = ref<boolean>(true)
const comment = ref('')

function openApprove(task: TaskVO, approved: boolean) {
  currentTask.value = task
  approveResult.value = approved
  comment.value = ''
  approveVisible.value = true
}

async function handleSubmit() {
  if (!currentTask.value) return
  const data: TaskCompleteDTO = {
    approved: approveResult.value,
    comment: comment.value
  }
  try {
    await completeTask(currentTask.value.taskId, data)
    message.success(approveResult.value ? '已通过' : '已拒绝')
    approveVisible.value = false
    fetchTodo()
  } catch {}
}

const columns: any[] = [
  { title: '流程类型', dataIndex: 'flowType', width: 100,
    customRender: ({ record }: any) => typeText(record.flowType)
  },
  { title: '任务节点', dataIndex: 'taskName', width: 160 },
  { title: '申请人', dataIndex: 'bizId', width: 130,
    customRender: ({ record }: any) => empName(record.bizId)
  },
  { title: '创建时间', dataIndex: 'createTime', width: 180 },
  {
    title: '操作', key: 'action', width: 200, fixed: 'right',
    customRender: ({ record }: any) => h(Space, {}, () => [
      h(Button, { size: 'small', type: 'link', onClick: () => openApprove(record, true) },
        () => h(CheckOutlined, {}, () => ' 通过')),
      h(Button, { size: 'small', type: 'link', danger: true, onClick: () => openApprove(record, false) },
        () => h(CloseOutlined, {}, () => ' 拒绝'))
    ])
  }
]
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">我的待办</h2>
      <p class="page-subtitle">需要我处理的审批任务</p>
    </div>

    <div class="table-card">
      <div class="table-toolbar">
        <div class="table-toolbar-left">
          <strong>待办列表</strong>
          <span style="color: rgba(0,0,0,0.45); font-size: 13px">{{ dataSource.length }} 条待办</span>
        </div>
        <div class="table-toolbar-right">
          <Button :icon="h(ReloadOutlined)" @click="fetchTodo">刷新</Button>
        </div>
      </div>

      <Table :columns="columns" :data-source="dataSource" :loading="loading"
        :pagination="false" :scroll="{ x: 800 }" row-key="taskId">
        <template #emptyText>
          <div style="padding: 32px 0; color: rgba(0,0,0,0.45)">暂无待办任务</div>
        </template>
      </Table>
    </div>

    <!-- 审批弹窗 -->
    <Modal v-model:open="approveVisible" :title="approveResult ? '审批通过' : '审批拒绝'"
      @ok="handleSubmit" okText="确认" cancelText="取消" width="480px">
      <Descriptions :column="1" size="small" bordered style="margin-bottom: 16px">
        <Descriptions.Item label="流程类型">
          {{ typeText(currentTask?.flowType || '') }}
        </Descriptions.Item>
        <Descriptions.Item label="任务节点">{{ currentTask?.taskName }}</Descriptions.Item>
        <Descriptions.Item label="申请人">{{ empName(currentTask?.bizId) }}</Descriptions.Item>
        <Descriptions.Item label="创建时间">{{ currentTask?.createTime }}</Descriptions.Item>
      </Descriptions>
      <p style="margin-bottom: 8px">审批意见</p>
      <Input.TextArea v-model:value="comment" :rows="3" placeholder="请输入审批意见（选填）" />
    </Modal>
  </div>
</template>
