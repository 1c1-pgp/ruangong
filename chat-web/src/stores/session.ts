import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

/** 后端 User / JwtAuthUser 序列化后的宽松结构 */
export interface UserInfo {
  uid?: string
  userId?: string | { $oid?: string }
  username?: string
  nickname?: string
  photo?: string
  code?: string
  signature?: string
  [key: string]: unknown
}

export function normalizeUid(u: UserInfo | null): string {
  if (!u) return ''
  if (u.uid && typeof u.uid === 'string') return u.uid
  const id = u.userId
  if (typeof id === 'string') return id
  if (id && typeof id === 'object' && '$oid' in id && typeof id.$oid === 'string') return id.$oid
  return ''
}

export const useSessionStore = defineStore('session', () => {
  const token = ref(localStorage.getItem('chat_token') || '')
  const userInfo = ref<UserInfo | null>(null)

  try {
    const raw = localStorage.getItem('chat_user')
    if (raw) userInfo.value = JSON.parse(raw) as UserInfo
  } catch {
    userInfo.value = null
  }

  const uid = computed(() => normalizeUid(userInfo.value))

  function setSession(t: string, user: UserInfo) {
    token.value = t
    userInfo.value = user
    localStorage.setItem('chat_token', t)
    localStorage.setItem('chat_user', JSON.stringify(user))
  }

  function clear() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('chat_token')
    localStorage.removeItem('chat_user')
  }

  return { token, userInfo, uid, setSession, clear }
})
