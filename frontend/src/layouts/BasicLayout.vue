<script setup lang="ts">
import { computed } from 'vue'
import { Layout, Menu, Button, Breadcrumb } from 'ant-design-vue'
import {
  DashboardOutlined, UserOutlined, TeamOutlined, FileTextOutlined,
  TrophyOutlined, BookOutlined, TagOutlined, LinkOutlined,
  MenuFoldOutlined, MenuUnfoldOutlined
} from '@ant-design/icons-vue'
import { useRoute, useRouter } from 'vue-router'

const { Header, Sider, Content } = Layout
const route = useRoute()
const router = useRouter()

const collapsed = defineModel<boolean>('collapsed', { default: false })

const menuItems = [
  { key: '/dashboard', label: '工作台', icon: DashboardOutlined },
  { key: '/employees', label: '员工管理', icon: UserOutlined },
  { key: '/job-posts', label: '岗位管理', icon: TeamOutlined },
  { key: '/resumes', label: '简历管理', icon: FileTextOutlined },
  { key: '/performances', label: '绩效管理', icon: TrophyOutlined },
  { key: '/training-courses', label: '培训课程', icon: BookOutlined },
  { key: '/ability-tags', label: '能力标签', icon: TagOutlined },
  { key: '/resume-ability-rels', label: '简历能力关联', icon: LinkOutlined }
]

const selectedKeys = computed(() => [route.path])
const title = computed(() => (route.meta.title as string) || '')

function handleMenuClick(info: any) {
  const key = String(info.key)
  if (key !== route.path) router.push(key)
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
          <Button
            type="text"
            @click="collapsed = !collapsed"
          >
            <MenuUnfoldOutlined v-if="collapsed" />
            <MenuFoldOutlined v-else />
          </Button>
          <Breadcrumb>
            <Breadcrumb.Item>HR-Agent</Breadcrumb.Item>
            <Breadcrumb.Item>{{ title }}</Breadcrumb.Item>
          </Breadcrumb>
        </div>
        <div class="header-right">智能人力助手 · 管理后台</div>
      </Header>
      <Content>
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
  padding: 0 20px;
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
  color: rgba(0, 0, 0, 0.45);
  font-size: 13px;
}
</style>
