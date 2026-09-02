<script setup lang="ts">
import { Row, Col, Card, Statistic, Input, Button } from 'ant-design-vue'
import {
  UserOutlined,
  TeamOutlined,
  FileTextOutlined,
  TrophyOutlined,
  ArrowUpOutlined,
  ArrowDownOutlined,
  RobotOutlined,
  SendOutlined
} from '@ant-design/icons-vue'
import { onMounted, ref, computed, h } from 'vue'
import { useRouter } from 'vue-router'
import { employeeApi } from '@/api/modules/employee'
import { jobPostApi } from '@/api/modules/jobPost'
import { resumeApi } from '@/api/modules/resume'
import { performanceApi } from '@/api/modules/performance'

const router = useRouter()
const currentUser = ref(JSON.parse(localStorage.getItem('hragent_user') || '{}'))
const quickAsk = ref('')

const todayText = computed(() => {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 星期${week}`
})

const stats = ref({
  employees: 0,
  jobPosts: 0,
  resumes: 0,
  performances: 0
})

onMounted(async () => {
  try {
    const [emp, job, resume, perf] = await Promise.all([
      employeeApi.page({ pageNum: 1, pageSize: 1 }),
      jobPostApi.page({ pageNum: 1, pageSize: 1 }),
      resumeApi.page({ pageNum: 1, pageSize: 1 }),
      performanceApi.page({ pageNum: 1, pageSize: 1 })
    ])
    stats.value = {
      employees: emp.total,
      jobPosts: job.total,
      resumes: resume.total,
      performances: perf.total
    }
  } catch { /* ignore */ }
})

// 快捷提问：跳转 AI 助手并带入问题
const quickPrompts = [
  '查询员工张三的信息',
  '发起一名员工的入职流程',
  '本月有哪些绩效结果'
]
function askAgent(text?: string) {
  const q = (text ?? quickAsk.value).trim()
  router.push({ path: '/agent', query: q ? { q } : {} })
  quickAsk.value = ''
}

const cards = [
  { title: '在职员工', value: 'employees', icon: UserOutlined, trend: '+2.3%', up: true, color: '#4a7fc1' },
  { title: '开放岗位', value: 'jobPosts', icon: TeamOutlined, trend: '+5.1%', up: true, color: '#13C2C2' },
  { title: '简历池', value: 'resumes', icon: FileTextOutlined, trend: '-1.2%', up: false, color: '#FA8C16' },
  { title: '绩效记录', value: 'performances', icon: TrophyOutlined, trend: '+8.7%', up: true, color: '#722ED1' }
]

const hoverBg = ref<Record<string, string | undefined>>({})
function onShortcutEnter(path: string, color: string, e: MouseEvent) {
  hoverBg.value = { ...hoverBg.value, [path]: color + '15' }
  void e
}
function onShortcutLeave(path: string, e: MouseEvent) {
  hoverBg.value = { ...hoverBg.value, [path]: undefined }
  void e
}
</script>

<template>
  <div class="page-container">
    <!-- 问候区：HR 工作台 × AI 助手融合入口 -->
    <Card :bordered="false" class="greet-card">
      <div class="greet-inner">
        <div class="greet-left">
          <div class="greet-title-row">
            <h2 class="greet-title">你好，{{ currentUser.empName || 'HR' }}，欢迎回到 HR-Agent</h2>
            <span class="greet-date">{{ todayText }}</span>
          </div>
          <p class="greet-sub">管理员工、岗位、简历与绩效数据，或直接向 AI 助手提问。</p>
          <div class="greet-ask">
            <Input
              v-model:value="quickAsk"
              placeholder="问问 AI 助手：例如「查询员工张三的信息」"
              @pressEnter="askAgent()"
            />
            <Button type="primary" :icon="h(SendOutlined)" @click="askAgent()">提问</Button>
          </div>
          <div class="greet-prompts">
            <span v-for="p in quickPrompts" :key="p" class="prompt-chip" @click="askAgent(p)">{{ p }}</span>
          </div>
        </div>
        <div class="greet-right">
          <RobotOutlined class="greet-bot" />
        </div>
      </div>
    </Card>

    <Row :gutter="[16, 16]">
      <Col v-for="c in cards" :key="c.title" :xs="24" :sm="12" :lg="6">
        <Card :bordered="false" hoverable class="stat-card">
          <div style="display: flex; justify-content: space-between; align-items: flex-start">
            <div>
              <div style="color: rgba(0,0,0,0.45); font-size: 13px; margin-bottom: 8px">{{ c.title }}</div>
              <Statistic :value="(stats as any)[c.value]" :value-style="{ fontWeight: 600 }" />
              <div style="margin-top: 8px; font-size: 12px">
                <span :style="{ color: c.up ? '#52C41A' : '#FF4D4F' }">
                  <component :is="c.up ? ArrowUpOutlined : ArrowDownOutlined" /> {{ c.trend }}
                </span>
                <span style="color: rgba(0,0,0,0.45); margin-left: 6px">较上月</span>
              </div>
            </div>
            <div :style="{
              width: 44, height: 44, borderRadius: 10,
              background: `linear-gradient(135deg, ${c.color}15, ${c.color}35)`,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              color: c.color, fontSize: 22
            }">
              <component :is="c.icon" />
            </div>
          </div>
        </Card>
      </Col>
    </Row>

    <Row :gutter="[16, 16]" style="margin-top: 16px">
      <Col :xs="24" :lg="16">
        <Card :bordered="false" title="快捷入口">
          <div style="display: flex; flex-wrap: wrap; gap: 12px">
            <router-link v-for="item in [
              { path: '/employees', label: '员工管理', color: '#4a7fc1' },
              { path: '/job-posts', label: '岗位管理', color: '#13C2C2' },
              { path: '/resumes', label: '简历管理', color: '#FA8C16' },
              { path: '/performances', label: '绩效管理', color: '#722ED1' },
              { path: '/training-courses', label: '培训课程', color: '#EB2F96' },
              { path: '/ability-tags', label: '能力标签', color: '#52C41A' }
            ]" :key="item.path" :to="item.path" custom
              v-slot="{ navigate }">
              <a @click="navigate"
                :style="{
                  flex: '1 1 30%', minWidth: '140px', padding: '16px', borderRadius: '10px',
                  background: hoverBg?.[item.path] || '#fafafa', textDecoration: 'none', color: 'inherit',
                  transition: 'all 0.2s'
                }"
                @mouseenter="(e: MouseEvent) => onShortcutEnter(item.path, item.color, e)"
                @mouseleave="(e: MouseEvent) => onShortcutLeave(item.path, e)">
                <div style="font-weight: 500">{{ item.label }}</div>
                <div style="font-size: 12px; color: rgba(0,0,0,0.45); margin-top: 4px">进入模块 →</div>
              </a>
            </router-link>
          </div>
        </Card>
      </Col>
      <Col :xs="24" :lg="8">
        <Card :bordered="false" title="系统信息">
          <div style="color: rgba(0,0,0,0.65); line-height: 2">
            <div>版本：hr-agent-web 0.0.1</div>
            <div>框架：Vue 3 + Vite + Ant Design Vue 4</div>
            <div>后端：Spring Boot 3.3.4 + MyBatis-Plus</div>
            <div>端口：前端 5173 / 后端 8080</div>
          </div>
        </Card>
      </Col>
    </Row>
  </div>
</template>

<style scoped>
.greet-card {
  background: linear-gradient(135deg, #eef3f9 0%, #f7fafd 100%);
  border: 1px solid #e8eef5;
  border-radius: 14px;
  margin-bottom: 16px;
  overflow: hidden;
}
.greet-card :deep(.ant-card-body) {
  padding: 24px;
}
.greet-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}
.greet-left {
  flex: 1;
  min-width: 0;
}
.greet-title-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
  flex-wrap: wrap;
}
.greet-title {
  font-size: 20px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
  margin: 0;
}
.greet-date {
  font-size: 12px;
  color: #6b7280;
}
.greet-sub {
  font-size: 13px;
  color: #6b7280;
  margin: 6px 0 16px;
}
.greet-ask {
  display: flex;
  gap: 8px;
  max-width: 560px;
}
.greet-prompts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}
.prompt-chip {
  font-size: 12px;
  color: #4a7fc1;
  background: #ffffff;
  border: 1px solid #dbe4ee;
  padding: 3px 12px;
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.15s;
}
.prompt-chip:hover {
  background: #e8f0fe;
  border-color: #4a7fc1;
}
.greet-right {
  flex-shrink: 0;
  width: 88px;
  height: 88px;
  border-radius: 50%;
  background: linear-gradient(135deg, #4a7fc1, #7ba3d0);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 20px rgba(74, 127, 193, 0.25);
}
.greet-bot {
  font-size: 40px;
  color: #fff;
}
.stat-card {
  border-radius: 12px;
}
</style>
