<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Form, FormItem, Input, InputPassword, Button, message } from 'ant-design-vue'
import { UserOutlined, LockOutlined, RobotOutlined } from '@ant-design/icons-vue'
import { login, type LoginDTO } from '@/api/modules/auth'
import { setToken } from '@/api/request'

const router = useRouter()
const loading = ref(false)
const form = ref<LoginDTO>({ account: '', password: '' })

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
    <div class="login-card">
      <div class="login-header">
        <div class="login-bot"><RobotOutlined /></div>
        <h1 class="login-title">HR-Agent</h1>
        <p class="login-subtitle">智能人力助手 · 统一管理入口</p>
      </div>
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
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f7f8fa 0%, #e6edf5 100%);
}
.login-card {
  width: 380px;
  padding: 40px 36px 32px;
  background: #fff;
  border-radius: 14px;
  border: 1px solid #f3f4f6;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}
.login-header {
  text-align: center;
  margin-bottom: 28px;
}
.login-bot {
  width: 56px;
  height: 56px;
  margin: 0 auto 14px;
  border-radius: 50%;
  background: linear-gradient(135deg, #4a7fc1, #7ba3d0);
  color: #fff;
  font-size: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 20px rgba(74, 127, 193, 0.25);
}
.login-title {
  font-size: 22px;
  font-weight: 600;
  color: #1f2329;
  margin: 0 0 6px;
  letter-spacing: 0.5px;
}
.login-subtitle {
  font-size: 13px;
  color: #8a8f99;
  margin: 0;
}
.login-tip {
  text-align: center;
  font-size: 12px;
  color: #bbb;
  margin: 8px 0 0;
}
</style>
