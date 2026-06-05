<template>
  <div class="auth-page">
    <div class="auth-bg" />
    <div class="auth-panel">
      <div class="auth-brand">
        <div class="logo">✨</div>
        <h1>创建账号</h1>
        <p>加入即时通讯，开始聊天</p>
      </div>
      <el-form class="auth-form" label-position="top" @submit.prevent="onSubmit">
        <el-form-item label="用户名">
          <el-input v-model="username" size="large" autocomplete="username" placeholder="设置用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" size="large" type="password" show-password autocomplete="new-password" placeholder="设置密码" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="rePassword" size="large" type="password" show-password autocomplete="new-password" placeholder="再次输入密码" />
        </el-form-item>
        <el-form-item label="验证码">
          <div class="captcha-row">
            <el-input v-model="cvCode" size="large" maxlength="6" placeholder="验证码" />
            <el-button size="large" :loading="capLoading" @click="refreshCaptcha">刷新</el-button>
          </div>
          <div v-if="captchaHint" class="hint">开发环境验证码：{{ captchaHint }}</div>
        </el-form-item>
        <el-button type="primary" size="large" native-type="submit" :loading="loading" class="submit-btn">注册</el-button>
        <div class="links">
          <router-link to="/login">已有账号？去登录</router-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchCaptcha, register } from '@/api/auth'

const router = useRouter()
const username = ref('')
const password = ref('')
const rePassword = ref('')
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
    ElMessage.error('获取验证码失败')
  } finally {
    capLoading.value = false
  }
}

async function onSubmit() {
  if (password.value !== rePassword.value) {
    ElMessage.warning('两次密码不一致')
    return
  }
  loading.value = true
  try {
    const data = await register({
      username: username.value.trim(),
      password: password.value,
      rePassword: rePassword.value,
      cvCode: cvCode.value.trim(),
    })
    if (data.success && data.code === 1005) {
      ElMessage.success('注册成功，请登录')
      router.push('/login')
    } else {
      ElMessage.error(data.message || '注册失败')
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
    radial-gradient(ellipse 80% 60% at 80% 20%, rgba(99, 102, 241, 0.25), transparent),
    radial-gradient(ellipse 60% 50% at 20% 80%, rgba(52, 211, 153, 0.12), transparent),
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
