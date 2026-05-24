import io from 'socket.io-client'

let socket: ReturnType<typeof io> | null = null

/**
 * 开发：连当前页 origin，由 Vite 代理 /socket.io。
 * 生产同域：不传 VITE_SOCKET_ORIGIN，使用 window.location.origin。
 * 生产跨域：设置 VITE_SOCKET_ORIGIN 为 API 根地址（与 Socket 暴露域一致）。
 */
export function ensureSocket() {
  if (!socket) {
    const raw = import.meta.env.VITE_SOCKET_ORIGIN as string | undefined
    const origin =
      raw && raw.length > 0
        ? raw.replace(/\/$/, '')
        : typeof window !== 'undefined'
          ? window.location.origin
          : ''
    socket = io(origin, {
      path: '/socket.io/',
      transports: ['websocket'],
    })
  }
  return socket
}

export function teardownSocket() {
  try {
    if (socket?.connected) {
      socket.emit('leave')
    }
  } catch {
    /* ignore */
  }
  socket?.disconnect()
  socket = null
}

export function getSocket() {
  return socket
}
