<template>
  <div class="auth-wrap">
    <el-card class="auth-card" shadow="hover">
      <template #header>
        <span>注册</span>
      </template>
      <el-form label-position="top" @submit.prevent="onSubmit">
        <el-form-item label="用户名">
          <el-input v-model="username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="rePassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="验证码">
          <div class="captcha-row">
            <el-input v-model="cvCode" maxlength="6" />
            <el-button :loading="capLoading" @click="refreshCaptcha">刷新</el-button>
          </div>
          <div v-if="captchaHint" class="hint">当前验证码：{{ captchaHint }}</div>
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" style="width: 100%">注册</el-button>
        <div class="links">
          <router-link to="/login">已有账号？登录</router-link>
        </div>
      </el-form>
    </el-card>
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
