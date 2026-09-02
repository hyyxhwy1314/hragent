<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ConfigProvider, App as AntApp } from 'ant-design-vue'
import type { ThemeConfig } from 'ant-design-vue/es/config-provider/context'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import BasicLayout from '@/layouts/BasicLayout.vue'

const theme: ThemeConfig = {
  token: {
    colorPrimary: '#4a7fc1',
    colorInfo: '#4a7fc1',
    colorLink: '#4a7fc1',
    colorSuccess: '#52C41A',
    colorWarning: '#FAAD14',
    colorError: '#FF4D4F',
    colorBgLayout: '#f7f8fa',
    colorBgContainer: '#ffffff',
    colorBorder: '#e5e7eb',
    colorBorderSecondary: '#f3f4f6',
    borderRadius: 10,
    fontSize: 14,
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif'
  }
}

const route = useRoute()
// 登录页不套布局，独立全屏
const isLoginPage = computed(() => route.path === '/login')
</script>

<template>
  <ConfigProvider :theme="theme" :locale="zhCN">
    <AntApp>
      <router-view v-if="isLoginPage" />
      <BasicLayout v-else />
    </AntApp>
  </ConfigProvider>
</template>
