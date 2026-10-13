<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Form, FormItem, Input, InputPassword, Button, message } from 'ant-design-vue'
import { UserOutlined, LockOutlined, RobotOutlined, CheckCircleFilled } from '@ant-design/icons-vue'
import { login, type LoginDTO } from '@/api/modules/auth'
import { setToken } from '@/api/request'

const router = useRouter()
const loading = ref(false)
const form = ref<LoginDTO>({ account: '', password: '' })

const highlights = [
  '员工 / 岗位 / 简历 / 绩效数据统一管理',
  '入职 / 离职 / 调岗等流程一站式发起与审批',
  'AI 智能助手随时回答人力相关的问题'
]

async function handleLogin() {
  if (!form.value.account || !form.value.password) {
    message.warning('请输入工号和密码')
    return
  }
  loading.value = true
  try {
    const res = await login(form.value)
    setToken(res.token)
    // 缓存当前用户信息，侧边栏权限菜单用
    localStorage.setItem('hragent_user', JSON.stringify({
      empId: res.empId,
      empName: res.empName,
      role: res.role
    }))
    message.success(`欢迎回来，${res.empName}`)
    router.push('/dashboard')
  } catch (e) {
    // 错误已在响应拦截器中提示
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <!-- 左侧深色品牌区 -->
    <div class="brand-panel">
      <div class="brand-top">
        <div class="brand-bot"><RobotOutlined /></div>
        <div class="brand-title">HR-Agent</div>
        <div class="brand-sub">智能人力助手</div>
      </div>
      <div class="brand-features">
        <div v-for="item in highlights" :key="item" class="brand-feature">
          <CheckCircleFilled class="brand-feature-icon" />
          <span>{{ item }}</span>
        </div>
      </div>
      <div class="brand-footer">AI 驱动的企业人力管理平台</div>
    </div>

    <!-- 右侧浅色表单区 -->
    <div class="form-panel">
      <div class="form-inner">
        <h2 class="form-title">欢迎登录</h2>
        <p class="form-sub">使用您的工号与密码进入系统</p>
        <Form layout="vertical" @submit.prevent="handleLogin">
          <FormItem>
            <Input
              v-model:value="form.account"
              size="large"
              placeholder="工号 / 手机号"
              allow-clear
            >
              <template #prefix><UserOutlined /></template>
            </Input>
          </FormItem>
          <FormItem>
            <InputPassword
              v-model:value="form.password"
              size="large"
              placeholder="密码"
              @keyup.enter="handleLogin"
            >
              <template #prefix><LockOutlined /></template>
            </InputPassword>
          </FormItem>
          <FormItem>
            <Button
              type="primary"
              size="large"
              block
              :loading="loading"
              @click="handleLogin"
            >
              登 录
            </Button>
          </FormItem>
        </Form>
        <p class="login-tip">默认密码 123456</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  background: var(--bg-secondary);
}
/* 左侧深色品牌区（与深色侧栏身份一致） */
.brand-panel {
  flex: 1.1;
  min-width: 0;
  position: relative;
  background: linear-gradient(160deg, var(--sidebar-bg) 0%, var(--sidebar-bg-deep) 100%);
  color: var(--sidebar-text);
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 64px;
  overflow: hidden;
}
/* 细腻的网格氛围底纹（纯 CSS，无图片） */
.brand-panel::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.035) 1px, transparent 1px);
  background-size: 44px 44px;
  pointer-events: none;
}
.brand-panel > * {
  position: relative;
}
.brand-top { margin-bottom: 44px; }
.brand-bot {
  width: 56px;
  height: 56px;
  margin-bottom: 20px;
  border-radius: 14px;
  background: linear-gradient(135deg, var(--accent), var(--accent-hover));
  color: #fff;
  font-size: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 24px rgba(74, 127, 193, 0.35);
}
.brand-title {
  font-size: 28px;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 0.3px;
  margin-bottom: 6px;
}
.brand-sub { font-size: 14px; color: var(--sidebar-text-muted); }
.brand-features { display: flex; flex-direction: column; gap: 16px; margin-bottom: 44px; }
.brand-feature { display: flex; align-items: center; gap: 10px; font-size: 14px; color: var(--sidebar-text); }
.brand-feature-icon { color: var(--accent); font-size: 16px; flex-shrink: 0; }
.brand-footer { font-size: 12px; color: var(--sidebar-text-muted); }

/* 右侧浅色表单区 */
.form-panel { flex: 1; display: flex; align-items: center; justify-content: center; padding: 48px; }
.form-inner { width: 380px; }
.form-title { font-size: 22px; font-weight: 600; color: var(--text-primary); margin: 0 0 6px; letter-spacing: 0.2px; }
.form-sub { font-size: 13px; color: var(--text-tertiary); margin: 0 0 28px; }
.login-tip { text-align: center; font-size: 12px; color: var(--text-quaternary); margin: 8px 0 0; }
</style>
