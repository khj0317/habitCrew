import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, type ProxyOptions } from 'vite'

// 개발 중에는 /api 요청을 백엔드로 프록시해서 같은 주소처럼 쓴다 (리프레시 쿠키도 같은 출처로 오간다).
const backend = process.env.BACKEND_URL ?? 'http://localhost:8090'

// 브라우저 입장에서는 같은 출처 요청이므로 Origin 헤더를 지워 백엔드 CORS 검사를 피한다.
const stripOrigin: ProxyOptions['configure'] = (proxy) => {
  proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'))
}

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5180,
    strictPort: true,
    proxy: {
      '/api': { target: backend, configure: stripOrigin },
    },
  },
})
