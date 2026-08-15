import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import Dashboard from '@/views/dashboard/index.vue'
import Employee from '@/views/employee/index.vue'
import JobPost from '@/views/jobPost/index.vue'
import Resume from '@/views/resume/index.vue'
import Performance from '@/views/performance/index.vue'
import TrainingCourse from '@/views/trainingCourse/index.vue'
import AbilityTag from '@/views/abilityTag/index.vue'
import ResumeAbilityRel from '@/views/resumeAbilityRel/index.vue'

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', name: 'Dashboard', component: Dashboard, meta: { title: '工作台', icon: 'DashboardOutlined' } },
  { path: '/employees', name: 'Employee', component: Employee, meta: { title: '员工管理', icon: 'UserOutlined' } },
  { path: '/job-posts', name: 'JobPost', component: JobPost, meta: { title: '岗位管理', icon: 'TeamOutlined' } },
  { path: '/resumes', name: 'Resume', component: Resume, meta: { title: '简历管理', icon: 'FileTextOutlined' } },
  { path: '/performances', name: 'Performance', component: Performance, meta: { title: '绩效管理', icon: 'TrophyOutlined' } },
  { path: '/training-courses', name: 'TrainingCourse', component: TrainingCourse, meta: { title: '培训课程', icon: 'BookOutlined' } },
  { path: '/ability-tags', name: 'AbilityTag', component: AbilityTag, meta: { title: '能力标签', icon: 'TagOutlined' } },
  { path: '/resume-ability-rels', name: 'ResumeAbilityRel', component: ResumeAbilityRel, meta: { title: '简历能力关联', icon: 'LinkOutlined' } }
]

export default createRouter({
  history: createWebHashHistory(),
  routes
})
