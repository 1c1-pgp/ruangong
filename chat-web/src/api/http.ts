import axios from 'axios'
import { useSessionStore } from '@/stores/session'

export interface ApiEnvelope {
  success?: boolean
  code?: number
  message?: string
  data?: Record<string, unknown>
}

/** 生产环境前后端不同域时设置 VITE_API_ORIGIN，例如 https://api.example.com（不要末尾斜杠） */
const apiOrigin = (import.meta.env.VITE_API_ORIGIN as string | undefined)?.replace(/\/$/, '') ?? ''

const http = axios.create({
  baseURL: apiOrigin,
  timeout: 30000,
  withCredentials: true,
})

http.interceptors.request.use((config) => {
  const session = useSessionStore()
  const path = config.url || ''
  const skipAuth =
    path.includes('/user/login') ||
    path.includes('/user/register') ||
    path.includes('/user/getCode')
  if (!skipAuth && session.token) {
    config.headers.Authorization = session.token.startsWith('Bearer ')
      ? session.token
      : `Bearer ${session.token}`
  }
  return config
})

http.interceptors.response.use((response) => {
  const payload = response.data as ApiEnvelope | undefined
  if (payload?.code === 2002 || payload?.code === 1006) {
    const session = useSessionStore()
    session.clear()
    if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
      window.location.href = '/login'
    }
    return Promise.reject(new Error(payload.message || '登录已失效'))
  }
  return response
})

export default http
