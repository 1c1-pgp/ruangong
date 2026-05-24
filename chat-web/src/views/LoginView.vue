<template>
  <div class="auth-wrap">
    <el-card class="auth-card" shadow="hover">
      <template #header>
        <span>登录</span>
      </template>
      <el-form label-position="top" @submit.prevent="onSubmit">
        <el-form-item label="用户名">
          <el-input v-model="username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-form-item label="验证码">
          <div class="captcha-row">
            <el-input v-model="cvCode" maxlength="6" placeholder="右侧字符（开发接口会直接返回）" />
            <el-button :loading="capLoading" @click="refreshCaptcha">刷新</el-button>
          </div>
          <div v-if="captchaHint" class="hint">当前验证码：{{ captchaHint }}（真实环境应使用图形验证码）</div>
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" style="width: 100%">登录</el-button>
        <div class="links">
          <router-link to="/register">没有账号？注册</router-link>
        </div>
      </el-form>
    </el-card>
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
      const redirect = (route.query.redirect as string) || '/'
      router.replace(redirect)
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

onMounted(() => {
  refreshCaptcha()
})
</script>

<style scoped>
.auth-wrap {
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
}
.auth-card {
  width: 400px;
}
.captcha-row {
  display: flex;
  gap: 8px;
  width: 100%;
}
.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
.links {
  margin-top: 12px;
  text-align: center;
}
</style>
