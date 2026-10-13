<script setup lang="ts">
import { ref, computed } from 'vue'
import { Layout, Menu, Button, Breadcrumb, Dropdown, Avatar } from 'ant-design-vue'
import {
  DashboardOutlined, UserOutlined, TeamOutlined, FileTextOutlined,
  TrophyOutlined, BookOutlined, TagOutlined, LinkOutlined,
  MenuFoldOutlined, MenuUnfoldOutlined, LogoutOutlined, DownOutlined,
  ClockCircleOutlined, ApartmentOutlined, RobotOutlined, FileProtectOutlined, DeploymentUnitOutlined,
  IdcardOutlined, PlusOutlined
} from '@ant-design/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { clearToken } from '@/api/request'
import { logout } from '@/api/modules/auth'

const { Header, Sider, Content } = Layout
const route = useRoute()
const router = useRouter()
const collapsed = ref(false)

// 当前登录用户（登录时缓存）
const currentUser = ref(JSON.parse(localStorage.getItem('hragent_user') || '{}'))

// 全部菜单项，按 role 过滤显示
const allMenus = [
  { key: '/dashboard', label: '工作台', icon: DashboardOutlined, roles: ['EMPLOYEE', 'DEPT_LEADER', 'HR', 'HRBP', 'ADMIN'] },
  { key: '/agent', label: 'AI 助手', icon: RobotOutlined, roles: ['EMPLOYEE', 'DEPT_LEADER', 'HR', 'HRBP', 'ADMIN'] },
  { key: '/todo', label: '我的待办', icon: ClockCircleOutlined, roles: ['EMPLOYEE', 'DEPT_LEADER', 'HR', 'HRBP', 'ADMIN'] },
  { key: '/self-service', label: '员工自助', icon: IdcardOutlined, roles: ['EMPLOYEE', 'DEPT_LEADER', 'HR', 'HRBP', 'ADMIN'] },
  { key: '/flow', label: '流程管理', icon: ApartmentOutlined, roles: ['HR', 'ADMIN'] },
  { key: '/flow-designer', label: '流程设计器', icon: ApartmentOutlined, roles: ['HR', 'ADMIN'] },
  { key: '/employees', label: '员工管理', icon: UserOutlined, roles: ['HR', 'ADMIN'] },
  { key: '/job-posts', label: '岗位管理', icon: TeamOutlined, roles: ['EMPLOYEE', 'DEPT_LEADER', 'HR', 'HRBP', 'ADMIN'] },
  { key: '/resumes', label: '简历管理', icon: FileTextOutlined, roles: ['HR', 'ADMIN'] },
  { key: '/performances', label: '绩效管理', icon: TrophyOutlined, roles: ['DEPT_LEADER', 'HR', 'HRBP', 'ADMIN'] },
  { key: '/training-courses', label: '培训课程', icon: BookOutlined, roles: ['HR', 'ADMIN'] },
  { key: '/ability-tags', label: '能力标签', icon: TagOutlined, roles: ['HR', 'ADMIN'] },
  { key: '/resume-ability-rels', label: '简历能力关联', icon: LinkOutlined, roles: ['HR', 'ADMIN'] },
  { key: '/contract-review', label: '合同审查', icon: FileProtectOutlined, roles: ['HR', 'ADMIN'] },
  { key: '/architecture', label: '系统架构', icon: DeploymentUnitOutlined, roles: ['EMPLOYEE', 'DEPT_LEADER', 'HR', 'HRBP', 'ADMIN'] }
]

// 按当前用户角色过滤菜单，并分成「工作台」与「管理」两组
const menuItems = computed(() =>
  allMenus.filter(m => m.roles.includes(currentUser.value.role || 'EMPLOYEE'))
)
const primaryMenus = computed(() =>
  menuItems.value.filter(m => ['/dashboard', '/agent', '/todo', '/self-service'].includes(m.key))
)
const manageMenus = computed(() =>
  menuItems.value.filter(m => !['/dashboard', '/agent', '/todo', '/self-service'].includes(m.key))
)

const selectedKeys = computed(() => [route.path])
const title = computed(() => (route.meta.title as string) || '')

function handleMenuClick(info: any) {
  const key = String(info.key)
  if (key !== route.path) router.push(key)
}

/** 快速新建对话：跳转 AI 助手页（进入即新会话） */
function goAgent() {
  router.push('/agent')
}

async function handleLogout() {
  try { await logout() } catch {}
  clearToken()
  localStorage.removeItem('hragent_user')
  router.push('/login')
}

const roleLabels: Record<string, string> = {
  EMPLOYEE: '员工',
  DEPT_LEADER: '部门主管',
  HR: '人事',
  HRBP: 'HRBP',
  ADMIN: '管理员'
}
</script>

<template>
  <Layout class="app-root">
    <Sider v-model:collapsed="collapsed" collapsible trigger="null" width="232" class="app-sider">
      <div class="logo">
        <RobotOutlined class="logo-icon" />
        <span v-if="!collapsed" class="logo-text">
          <span class="logo-name">HR-Agent</span>
          <span class="logo-sub">智能人力助手</span>
        </span>
      </div>
      <div class="menu-wrap">
        <Menu
          mode="inline"
          :inline-collapsed="collapsed"
          :selected-keys="selectedKeys"
          @click="handleMenuClick"
        >
          <Menu.ItemGroup v-if="primaryMenus.length" key="primary">
            <template #title><span class="menu-group-title">工作台</span></template>
            <Menu.Item v-for="item in primaryMenus" :key="item.key">
              <component :is="item.icon" />
              <span>{{ item.label }}</span>
            </Menu.Item>
          </Menu.ItemGroup>
          <Menu.ItemGroup v-if="manageMenus.length" key="manage">
            <template #title><span class="menu-group-title">管理</span></template>
            <Menu.Item v-for="item in manageMenus" :key="item.key">
              <component :is="item.icon" />
              <span>{{ item.label }}</span>
            </Menu.Item>
          </Menu.ItemGroup>
        </Menu>
      </div>
    </Sider>
    <Layout class="app-main">
      <Header class="app-header">
        <div class="header-left">
          <Button type="text" @click="collapsed = !collapsed">
            <MenuUnfoldOutlined v-if="collapsed" />
            <MenuFoldOutlined v-else />
          </Button>
          <Button type="primary" ghost class="new-chat-btn" @click="goAgent">
            <PlusOutlined />
            <span>新建对话</span>
          </Button>
          <Breadcrumb>
            <Breadcrumb.Item>HR-Agent</Breadcrumb.Item>
            <Breadcrumb.Item>{{ title }}</Breadcrumb.Item>
          </Breadcrumb>
        </div>
        <div class="header-right">
          <Dropdown>
            <span class="user-info">
              <Avatar size="small" style="background-color: var(--accent)">
                {{ (currentUser.empName || '?').charAt(0) }}
              </Avatar>
              <span class="user-name">{{ currentUser.empName }}</span>
              <span class="user-role">{{ roleLabels[currentUser.role] || '' }}</span>
              <DownOutlined />
            </span>
            <template #overlay>
              <Menu>
                <Menu.Item key="logout" @click="handleLogout">
                  <LogoutOutlined />
                  <span>退出登录</span>
                </Menu.Item>
              </Menu>
            </template>
          </Dropdown>
        </div>
      </Header>
      <Content class="app-content">
        <router-view />
      </Content>
    </Layout>
  </Layout>
</template>

<style scoped>
/* 根布局锁定视口高度：侧栏固定、仅内容区内部滚动 */
.app-root {
  height: 100vh;
  overflow: hidden;
}
.app-main {
  min-width: 0;
}
.app-content {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.app-sider {
  background: var(--sidebar-bg);
  border-right: 1px solid var(--sidebar-border);
  display: flex;
  flex-direction: column;
}
.menu-wrap {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
}
.logo {
  height: 64px;
  margin: 0 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border-bottom: 1px solid var(--sidebar-border);
  transition: all 0.2s;
}
.logo-icon {
  font-size: 22px;
  color: var(--accent);
  flex-shrink: 0;
}
.logo-text {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
  min-width: 0;
}
.logo-name {
  font-size: 16px;
  font-weight: 600;
  color: #eef1f5;
  letter-spacing: 0.4px;
}
.logo-sub {
  font-size: 11px;
  color: var(--sidebar-text-muted);
}
:deep(.app-sider .ant-menu) {
  border-inline-end: none;
  padding: 8px 12px;
  background: transparent;
}
:deep(.app-sider .ant-menu-item-group-title) {
  padding: 16px 12px 6px;
  font-size: 11px;
  color: var(--sidebar-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  font-weight: 500;
  line-height: 1.4;
}
:deep(.app-sider .ant-menu-item) {
  height: 40px;
  line-height: 40px;
  border-radius: 8px;
  margin-bottom: 4px;
  color: var(--sidebar-text);
  transition: all 0.15s;
}
:deep(.app-sider .ant-menu-item:hover) {
  background: var(--sidebar-hover);
  color: #ffffff;
}
:deep(.app-sider .ant-menu-item-selected) {
  background: var(--sidebar-active);
  color: #ffffff;
  font-weight: 500;
  box-shadow: inset 3px 0 0 var(--accent);
}
:deep(.app-sider .ant-menu-item-selected::after) {
  display: none;
}
.app-header {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: saturate(180%) blur(8px);
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--border);
  box-shadow: var(--shadow-sm);
  height: 56px;
  line-height: 56px;
  position: relative;
  z-index: 10;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.new-chat-btn {
  border-radius: 8px;
  font-weight: 500;
  margin-right: 4px;
}
.header-right {
  display: flex;
  align-items: center;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: rgba(0, 0, 0, 0.65);
  padding: 4px 8px;
  border-radius: 6px;
  transition: background 0.15s;
}
.user-info:hover {
  background: var(--bg-hover);
}
.user-name {
  font-size: 14px;
}
.user-role {
  font-size: 12px;
  color: var(--text-tertiary);
  padding: 1px 6px;
  background: var(--bg-tertiary);
  border-radius: 4px;
}
</style>
