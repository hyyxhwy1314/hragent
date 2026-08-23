<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ConfigProvider, App as AntApp } from 'ant-design-vue'
import type { ThemeConfig } from 'ant-design-vue/es/config-provider/context'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import BasicLayout from '@/layouts/BasicLayout.vue'

const theme: ThemeConfig = {
  token: {
    colorPrimary: '#2F54EB',
    colorInfo: '#2F54EB',
    colorSuccess: '#52C41A',
    colorWarning: '#FAAD14',
    colorError: '#FF4D4F',
    borderRadius: 6,
    fontSize: 14,
    fontFamily: '"PingFang SC", "Microsoft YaHei", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif'
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
