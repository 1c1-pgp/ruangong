import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/chat': {
        target: 'http://localhost:5555',
        changeOrigin: true,
      },
      '/socket.io': {
        target: 'http://localhost:9999',
        ws: true,
        changeOrigin: true,
      },
    },
  },
})
