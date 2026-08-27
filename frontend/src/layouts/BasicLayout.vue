<script setup lang="ts">
import { ref, computed } from 'vue'
import { Layout, Menu, Button, Breadcrumb, Dropdown, Avatar } from 'ant-design-vue'
import {
  DashboardOutlined, UserOutlined, TeamOutlined, FileTextOutlined,
  TrophyOutlined, BookOutlined, TagOutlined, LinkOutlined,
  MenuFoldOutlined, MenuUnfoldOutlined, LogoutOutlined, DownOutlined,
  ClockCircleOutlined, ApartmentOutlined, RobotOutlined
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
  { key: '/flow', label: '流程管理', icon: ApartmentOutlined, roles: ['HR', 'ADMIN'] },
  { key: '/employees', label: '员工管理', icon: UserOutlined, roles: ['HR', 'ADMIN'] },
  { key: '/job-posts', label: '岗位管理', icon: TeamOutlined, roles: ['EMPLOYEE', 'DEPT_LEADER', 'HR', 'HRBP', 'ADMIN'] },
  { key: '/resumes', label: '简历管理', icon: FileTextOutlined, roles: ['HR', 'ADMIN'] },
  { key: '/performances', label: '绩效管理', icon: TrophyOutlined, roles: ['DEPT_LEADER', 'HR', 'HRBP', 'ADMIN'] },
  { key: '/training-courses', label: '培训课程', icon: BookOutlined, roles: ['HR', 'ADMIN'] },
  { key: '/ability-tags', label: '能力标签', icon: TagOutlined, roles: ['HR', 'ADMIN'] },
  { key: '/resume-ability-rels', label: '简历能力关联', icon: LinkOutlined, roles: ['HR', 'ADMIN'] }
]

// 按当前用户角色过滤菜单
const menuItems = computed(() =>
  allMenus.filter(m => m.roles.includes(currentUser.value.role || 'EMPLOYEE'))
)

const selectedKeys = computed(() => [route.path])
const title = computed(() => (route.meta.title as string) || '')

function handleMenuClick(info: any) {
  const key = String(info.key)
  if (key !== route.path) router.push(key)
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
  <Layout style="min-height: 100vh">
    <Sider v-model:collapsed="collapsed" collapsible trigger="null" width="216">
      <div class="logo">
        {{ collapsed ? 'HR' : 'HR-Agent' }}
      </div>
      <Menu
        theme="dark"
        mode="inline"
        :selected-keys="selectedKeys"
        @click="handleMenuClick"
      >
        <Menu.Item v-for="item in menuItems" :key="item.key">
          <component :is="item.icon" />
          <span>{{ item.label }}</span>
        </Menu.Item>
      </Menu>
    </Sider>
    <Layout>
      <Header class="app-header">
        <div class="header-left">
          <Button type="text" @click="collapsed = !collapsed">
            <MenuUnfoldOutlined v-if="collapsed" />
            <MenuFoldOutlined v-else />
          </Button>
          <Breadcrumb>
            <Breadcrumb.Item>HR-Agent</Breadcrumb.Item>
            <Breadcrumb.Item>{{ title }}</Breadcrumb.Item>
          </Breadcrumb>
        </div>
        <div class="header-right">
          <Dropdown>
            <span class="user-info">
              <Avatar size="small" style="background-color: #2F54EB">
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
      <Content style="overflow: hidden;">
        <router-view />
      </Content>
    </Layout>
  </Layout>
</template>

<style scoped>
.logo {
  height: 48px;
  margin: 12px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 600;
  font-size: 16px;
  letter-spacing: 1px;
  transition: all 0.2s;
}
.app-header {
  background: #fff;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #f0f0f0;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.04);
  height: 56px;
  line-height: 56px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
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
}
.user-name {
  font-size: 14px;
}
.user-role {
  font-size: 12px;
  color: #8a8f99;
  padding: 1px 6px;
  background: #f0f2f5;
  border-radius: 4px;
}
</style>
