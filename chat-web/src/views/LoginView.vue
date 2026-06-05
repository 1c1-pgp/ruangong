<template>
  <div class="auth-page">
    <div class="auth-bg" />
    <div class="auth-panel">
      <div class="auth-brand">
        <div class="logo">💬</div>
        <h1>即时通讯</h1>
        <p>连接好友，畅聊无阻</p>
      </div>
      <el-form class="auth-form" label-position="top" @submit.prevent="onSubmit">
        <el-form-item label="用户名">
          <el-input v-model="username" size="large" autocomplete="username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" size="large" type="password" show-password autocomplete="current-password" placeholder="请输入密码" />
        </el-form-item>
        <el-form-item label="验证码">
          <div class="captcha-row">
            <el-input v-model="cvCode" size="large" maxlength="6" placeholder="验证码" />
            <el-button size="large" :loading="capLoading" @click="refreshCaptcha">刷新</el-button>
          </div>
          <div v-if="captchaHint" class="hint">开发环境验证码：{{ captchaHint }}</div>
        </el-form-item>
        <el-button type="primary" size="large" native-type="submit" :loading="loading" class="submit-btn">登录</el-button>
        <div class="links">
          <router-link to="/register">没有账号？立即注册</router-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchCaptcha, login } from '@/api/auth'
import { useSessionStore, type UserInfo } from '@/stores/session'

const router = useRouter()
const route = useRoute()
const session = useSessionStore()

const username = ref('')
const password = ref('')
const cvCode = ref('')
const captchaHint = ref('')
const loading = ref(false)
const capLoading = ref(false)

async function refreshCaptcha() {
  capLoading.value = true
  try {
    const { code } = await fetchCaptcha()
    captchaHint.value = code
    cvCode.value = code
  } catch {
    ElMessage.error('获取验证码失败，请确认后端已启动')
  } finally {
    capLoading.value = false
  }
}

async function onSubmit() {
  loading.value = true
  try {
    const data = await login({
      username: username.value.trim(),
      password: password.value,
      cvCode: cvCode.value.trim(),
    })
    if (data.success && data.code === 1000 && data.data?.token && data.data?.userInfo) {
      session.setSession(data.data.token as string, data.data.userInfo as UserInfo)
      router.replace((route.query.redirect as string) || '/')
      ElMessage.success('登录成功')
    } else {
      ElMessage.error(data.message || '登录失败')
    }
  } catch {
    ElMessage.error('网络错误')
  } finally {
    loading.value = false
  }
}

onMounted(refreshCaptcha)
</script>

<style scoped>
.auth-page {
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}
.auth-bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse 80% 60% at 20% 20%, rgba(99, 102, 241, 0.25), transparent),
    radial-gradient(ellipse 60% 50% at 80% 80%, rgba(168, 85, 247, 0.15), transparent),
    var(--im-bg);
}
.auth-panel {
  position: relative;
  width: min(420px, 92vw);
  padding: 40px 36px;
  border-radius: 20px;
  background: rgba(26, 35, 50, 0.85);
  border: 1px solid var(--im-border);
  box-shadow: var(--im-shadow);
  backdrop-filter: blur(12px);
}
.auth-brand {
  text-align: center;
  margin-bottom: 32px;
}
.logo {
  font-size: 48px;
  margin-bottom: 8px;
}
.auth-brand h1 {
  margin: 0 0 8px;
  font-size: 24px;
  font-weight: 700;
}
.auth-brand p {
  margin: 0;
  color: var(--im-muted);
  font-size: 14px;
}
.captcha-row {
  display: flex;
  gap: 10px;
  width: 100%;
}
.hint {
  font-size: 12px;
  color: var(--im-muted);
  margin-top: 6px;
}
.submit-btn {
  width: 100%;
  margin-top: 8px;
  height: 44px;
  font-size: 16px;
}
.links {
  margin-top: 20px;
  text-align: center;
}
.links a {
  color: var(--im-accent-hover);
  text-decoration: none;
  font-size: 14px;
}
.links a:hover {
  text-decoration: underline;
}
</style>
