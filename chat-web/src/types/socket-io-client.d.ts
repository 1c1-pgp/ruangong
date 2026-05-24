declare module 'socket.io-client' {
  export interface Socket {
    on(event: string, fn: (...args: unknown[]) => void): Socket
    emit(event: string, ...args: unknown[]): Socket
    disconnect(): Socket
    connected: boolean
  }
  export default function io(uri?: string, opts?: Record<string, unknown>): Socket
}
