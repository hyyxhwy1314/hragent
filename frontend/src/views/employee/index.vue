<script setup lang="ts">
import { ref, computed, h, reactive } from 'vue'
import { Table, Button, Space, Input, Select, Form, message, Tag } from 'ant-design-vue'
import { PlusOutlined, SearchOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { employeeApi } from '@/api/modules/employee'
import { useCrud } from '@/composables/useCrud'

defineOptions({ name: 'EmployeePage' })

const queryForm = ref<Record<string, any>>({})
const crud = reactive(useCrud(employeeApi))
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
  showTotal: (t) => `共 ${t} 条`
}))

function onSearch() { crud.reload({ ...queryForm.value }) }
function onReset() { queryForm.value = {}; crud.reload() }
function onCreate() { message.info('新增功能开发中') }

const columns = [
  { title: '工号', dataIndex: 'empNo', width: 110 },
  { title: '姓名', dataIndex: 'empName', width: 110 },
  {
    title: '性别', dataIndex: 'gender', width: 70,
    customRender: ({ record }: any) => record.gender === 1 ? '男' : record.gender === 0 ? '女' : '-'
  },
  { title: '部门', dataIndex: 'deptName', width: 140 },
  { title: '职位', dataIndex: 'positionName', width: 140 },
  { title: '手机号', dataIndex: 'phone', width: 130 },
  { title: '入职日期', dataIndex: 'entryDate', width: 120 },
  {
    title: '状态', dataIndex: 'empStatus', width: 90,
    customRender: ({ record }: any) => {
      const map: Record<number, { text: string; color: string }> = {
        1: { text: '在职', color: 'green' },
        0: { text: '离职', color: 'red' },
        2: { text: '试用', color: 'orange' }
      }
      const s = map[record.empStatus] || { text: '-', color: 'default' }
      return h(Tag, { color: s.color }, () => s.text)
    }
  },
  {
    title: '操作', key: 'action', width: 200,
    customRender: ({ record }: any) =>
      h(Space, {}, () => [
        h(Button, { size: 'small', type: 'link', onClick: () => message.info('查看: ' + record.empNo) }, () => '查看'),
        h(Button, { size: 'small', type: 'link', onClick: () => message.info('编辑: ' + record.empNo) }, () => '编辑'),
        h(Button, { size: 'small', type: 'link', danger: true, onClick: () => message.warning('删除: ' + record.empNo) }, () => '删除')
      ])
  }
]
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">员工管理</h2>
      <p class="page-subtitle">管理企业全部员工档案信息</p>
    </div>

    <div class="search-card">
      <Form layout="inline" :model="queryForm" :label-col="{ style: { width: 60 } }">
        <Form.Item label="工号">
          <Input v-model:value="queryForm.empNo" placeholder="请输入工号" allow-clear style="width: 160px" />
        </Form.Item>
        <Form.Item label="姓名">
          <Input v-model:value="queryForm.empName" placeholder="请输入姓名" allow-clear style="width: 160px" />
        </Form.Item>
        <Form.Item label="状态">
          <Select v-model:value="queryForm.empStatus" placeholder="全部" allow-clear style="width: 120px"
            :options="[{ label: '在职', value: 1 }, { label: '离职', value: 0 }, { label: '试用', value: 2 }]" />
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
          <strong>员工列表</strong>
          <span style="color: rgba(0,0,0,0.45); font-size: 13px">共 {{ crud.total }} 条</span>
        </div>
        <div class="table-toolbar-right">
          <Button type="primary" :icon="h(PlusOutlined)" @click="onCreate">新增员工</Button>
        </div>
      </div>

      <Table
        :columns="columns"
        :data-source="safeDataSource"
        :loading="crud.loading"
        :pagination="pagination"
        :scroll="{ x: 1100 }"
        row-key="id"
        @change="crud.handleTableChange"
      />
    </div>
  </div>
</template>
