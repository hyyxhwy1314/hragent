import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import { getToken } from '@/api/request'
import Dashboard from '@/views/dashboard/index.vue'
import Employee from '@/views/employee/index.vue'
import JobPost from '@/views/jobPost/index.vue'
import Resume from '@/views/resume/index.vue'
import Performance from '@/views/performance/index.vue'
import TrainingCourse from '@/views/trainingCourse/index.vue'
import AbilityTag from '@/views/abilityTag/index.vue'
import ResumeAbilityRel from '@/views/resumeAbilityRel/index.vue'

const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'Login', component: () => import('@/views/login/index.vue'), meta: { title: '登录', public: true } },
  { path: '/', redirect: '/dashboard' },
  { path: '/agent', name: 'Agent', component: () => import('@/views/agent/index.vue'), meta: { title: 'AI 助手', icon: 'RobotOutlined' } },
  { path: '/dashboard', name: 'Dashboard', component: Dashboard, meta: { title: '工作台', icon: 'DashboardOutlined' } },
  { path: '/todo', name: 'Todo', component: () => import('@/views/todo/index.vue'), meta: { title: '我的待办', icon: 'ClockCircleOutlined' } },
  { path: '/flow', name: 'Flow', component: () => import('@/views/flow/index.vue'), meta: { title: '流程管理', icon: 'ApartmentOutlined', roles: ['HR', 'ADMIN'] } },
  { path: '/employees', name: 'Employee', component: Employee, meta: { title: '员工管理', icon: 'UserOutlined', roles: ['HR', 'ADMIN'] } },
  { path: '/job-posts', name: 'JobPost', component: JobPost, meta: { title: '岗位管理', icon: 'TeamOutlined' } },
  { path: '/resumes', name: 'Resume', component: Resume, meta: { title: '简历管理', icon: 'FileTextOutlined' } },
  { path: '/performances', name: 'Performance', component: Performance, meta: { title: '绩效管理', icon: 'TrophyOutlined' } },
  { path: '/training-courses', name: 'TrainingCourse', component: TrainingCourse, meta: { title: '培训课程', icon: 'BookOutlined' } },
  { path: '/ability-tags', name: 'AbilityTag', component: AbilityTag, meta: { title: '能力标签', icon: 'TagOutlined' } },
  { path: '/resume-ability-rels', name: 'ResumeAbilityRel', component: ResumeAbilityRel, meta: { title: '简历能力关联', icon: 'LinkOutlined' } }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

// 路由守卫：未登录跳登录页
router.beforeEach((to, _from, next) => {
  if (to.meta.public) {
    next()
    return
  }
  if (!getToken()) {
    next('/login')
    return
  }
  next()
})

export default router
