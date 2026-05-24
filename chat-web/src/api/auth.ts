import http, { type ApiEnvelope } from './http'

export async function fetchCaptcha(): Promise<{ code: string }> {
  const { data } = await http.get<ApiEnvelope>('/chat/user/getCode')
  const inner = data.data?.code
  return { code: typeof inner === 'string' ? inner : '' }
}

export async function login(payload: {
  username: string
  password: string
  cvCode: string
  avatar?: string
  setting?: Record<string, string>
}): Promise<ApiEnvelope> {
  const { data } = await http.post<ApiEnvelope>('/chat/user/login', {
    username: payload.username,
    password: payload.password,
    cvCode: payload.cvCode,
    avatar: payload.avatar ?? '',
    setting: payload.setting ?? {
      browser: typeof navigator !== 'undefined' ? navigator.userAgent : '',
      os: '',
      ip: '',
      country: '',
    },
  })
  return data
}

export async function register(payload: {
  username: string
  password: string
  rePassword: string
  avatar?: string
  cvCode: string
}): Promise<ApiEnvelope> {
  const { data } = await http.post<ApiEnvelope>('/chat/user/register', {
    username: payload.username,
    password: payload.password,
    rePassword: payload.rePassword,
    avatar: payload.avatar ?? '/img/picture.png',
    cvCode: payload.cvCode,
  })
  return data
}
