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
    colorSuccess: '#3f9a44',
    colorWarning: '#d48806',
    colorError: '#d64545',
    colorBgLayout: '#f6f7f9',
    colorBgContainer: '#ffffff',
    colorText: '#1f2733',
    colorTextSecondary: '#55606e',
    colorTextTertiary: '#8a94a3',
    colorBorder: '#e3e7ec',
    colorBorderSecondary: '#eef1f5',
    borderRadius: 10,
    borderRadiusLG: 14,
    borderRadiusSM: 6,
    controlHeight: 36,
    fontSize: 14,
    lineHeight: 1.6,
    fontFamily: 'var(--font-sans)',
    boxShadow: '0 1px 2px rgba(16,24,40,.04), 0 1px 3px rgba(16,24,40,.05)',
    boxShadowSecondary: '0 8px 24px rgba(16,24,40,.10)'
  },
  // 组件级令牌：本版本 ant-design-vue 的 ComponentTokenMap 类型未声明这些字段，
  // 运行时支持，故此处断言类型以通过编译（未支持的字段会被运行时忽略）
  components: {
    Table: {
      headerBg: '#eef1f5',
      headerColor: '#55606e',
      headerSplitColor: 'transparent',
      rowHoverBg: '#f4f7fb',
      cellPaddingBlock: 13,
      borderColor: '#eef1f5'
    },
    Menu: {
      itemBorderRadius: 8,
      itemHeight: 40,
      itemSelectedBg: 'rgba(74,127,193,.22)',
      darkItemBg: 'transparent',
      darkItemSelectedBg: 'rgba(74,127,193,.22)',
      darkItemColor: '#cbd2dc'
    },
    Card: {
      borderRadiusLG: 14,
      headerFontSize: 15,
      headerHeight: 48,
      paddingLG: 20
    },
    Button: {
      fontWeight: 500,
      primaryShadow: 'none'
    },
    Input: {
      activeShadow: '0 0 0 3px rgba(74,127,193,.16)'
    },
    Modal: {
      borderRadiusLG: 14,
      titleFontSize: 17
    },
    Tabs: {
      titleFontSize: 14,
      inkBarColor: '#4a7fc1',
      itemSelectedColor: '#1f2733'
    },
    Tag: {
      defaultBg: '#eef1f5',
      defaultColor: '#55606e'
    }
  } as ThemeConfig['components']
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
