<template>
  <el-dialog :visible.sync="visible" title="编辑资料" width="420px" destroy-on-close>
    <el-form label-position="top" :model="form">
      <el-form-item label="昵称">
        <el-input v-model="form.nickname" maxlength="30" show-word-limit />
      </el-form-item>
      <el-form-item label="头像">
        <div style="display:flex; gap:12px; align-items:center">
          <el-avatar :size="64" :src="preview || avatarPreview(session.userInfo?.photo)" />
          <div style="display:flex; flex-direction:column; gap:8px">
            <input ref="fileInput" type="file" accept="image/*" hidden @change="onFileChange" />
            <el-button size="small" @click="chooseFile">选择图片</el-button>
            <div style="font-size:12px; color:var(--im-muted)">支持 jpg/png/webp/gif，最大 10MB</div>
          </div>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useSessionStore } from '@/stores/session'
import { uploadChatFile, uploadChatFileWithDebug, updateUserInfo } from '@/api/chat'

const props = defineProps<{ visible: boolean; mode?: 'avatar' | 'nickname' | '' }>()
const emit = defineEmits(['update:visible', 'updated'])

const visible = ref(props.visible)
watch(() => props.visible, (v) => (visible.value = v))
watch([
  () => props.visible,
  () => props.mode,
], async ([v, m]) => {
  visible.value = v
  if (v && m === 'avatar') {
    // small delay to ensure dialog mounted
    await new Promise((r) => setTimeout(r, 80))
    chooseFile()
  }
  if (v) {
    form.value.nickname = session.userInfo?.nickname || ''
  }
})

// sync visible back to parent when changed locally
watch(visible, (v) => {
  emit('update:visible', v)
})

const fileInput = ref<HTMLInputElement | null>(null)
const preview = ref<string | undefined>(undefined)
const avatarPath = ref<string | undefined>(undefined)
const submitting = ref(false)

const session = useSessionStore()

const form = ref({ nickname: session.userInfo?.nickname || '' })

function avatarPreview(p?: string) {
  if (!p) return undefined
  if (p.startsWith('http')) return p
  if (p.startsWith('/chat')) return p
  return `/chat${p.startsWith('/') ? '' : '/'}${p}`
}

function chooseFile() { fileInput.value?.click() }

async function onFileChange(e: Event) {
  const f = (e.target as HTMLInputElement).files?.[0]
  if (!f) return
  const ext = (f.name.split('.').pop() || '').toLowerCase()
  const allowed = ['jpg','jpeg','png','gif','webp','bmp']
  const MAX = 10 * 1024 * 1024
  if (!allowed.includes(ext)) { ElMessage.error('不支持的图片格式'); return }
  if (f.size > MAX) { ElMessage.error('图片过大，最大 10MB'); return }
  preview.value = URL.createObjectURL(f)
  try {
    const res = await uploadChatFileWithDebug(f, undefined)
    avatarPath.value = res.filePath
    ElMessage.success('头像上传成功')
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '上传失败')
  }
}

function close() { emit('update:visible', false) }

async function submit() {
  const uid = session.uid
  if (!uid) return ElMessage.error('未登录')
  const nickname = form.value.nickname?.trim()
  if (!nickname) return ElMessage.warning('请输入昵称')
  submitting.value = true
  try {
    // update nickname
    if (nickname !== session.userInfo?.nickname) {
      await updateUserInfo('nickname', nickname, uid)
    }
    // update avatar if changed
    if (avatarPath.value && avatarPath.value !== session.userInfo?.photo) {
      await updateUserInfo('photo', avatarPath.value, uid)
    }
    // update local session
    const newUser = { ...(session.userInfo || {}), nickname, photo: avatarPath.value || session.userInfo?.photo }
    const tokenStr = (session.token as unknown as string) || ''
    session.setSession(tokenStr, newUser as any)
    ElMessage.success('更新成功')
    emit('updated')
    close()
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '更新失败')
  } finally { submitting.value = false }
}
</script>

<style scoped>
.el-dialog__body { padding-bottom: 8px }
</style>
