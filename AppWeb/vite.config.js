import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    host: '127.0.0.1',
    port: 5174,
    strictPort: false,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true,
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/swagger-ui': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/v3/api-docs': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/webjars': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
