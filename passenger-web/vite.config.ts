import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The passenger app is fully static; the backend is a separate process.
// VITE_API_URL can point anywhere (defaults to same-origin, e.g. behind a
// reverse proxy that also serves this build).
export default defineConfig({
  plugins: [react()],
  define: { global: 'window' },
  server: {
    port: 5173,
    proxy: {
      '/api': process.env.VITE_DEV_PROXY || 'http://localhost:8080',
      '/ws': { target: 'http://localhost:8080', ws: true }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false
  }
})