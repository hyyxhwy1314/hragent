<script setup lang="ts">
import { ConfigProvider, App as AntApp } from 'ant-design-vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import BasicLayout from '@/layouts/BasicLayout.vue'

const theme = {
  token: {
    colorPrimary: '#2F54EB',
    colorInfo: '#2F54EB',
    colorSuccess: '#52C41A',
    colorWarning: '#FAAD14',
    colorError: '#FF4D4F',
    borderRadius: 6,
    fontSize: 14,
    fontFamily: '"PingFang SC", "Microsoft YaHei", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif'
  },
  components: {
    Layout: {
      headerBg: '#ffffff',
      siderBg: '#001529',
      bodyBg: '#f0f2f5'
    },
    Menu: {
      darkItemBg: '#001529',
      darkSubMenuItemBg: '#000c17',
      darkItemSelectedBg: '#2F54EB'
    },
    Table: {
      headerBg: '#fafafa',
      headerColor: '#1f1f1f',
      rowHoverBg: '#f7faff'
    },
    Button: {
      controlHeight: 32
    }
  }
}
</script>

<template>
  <ConfigProvider :theme="theme" :locale="zhCN">
    <AntApp>
      <BasicLayout />
    </AntApp>
  </ConfigProvider>
</template>
