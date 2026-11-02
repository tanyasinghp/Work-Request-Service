import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { loadEnv } from 'vite'
import { defineConfig } from 'vitest/config'

export default defineConfig(({ command, mode }) => {
  // On Vercel there is no nginx to proxy /api, so the bundle must call the deployed API directly.
  // Fail the build rather than ship a site that quietly calls localhost.
  if (command === 'build' && process.env.VERCEL) {
    const apiUrl = loadEnv(mode, process.cwd(), 'VITE_').VITE_API_URL?.trim() ?? ''
    if (!/^https:\/\/[^/]+/.test(apiUrl)) {
      throw new Error(`VITE_API_URL must be the deployed API's https URL on Vercel, got "${apiUrl}"`)
    }
  }
  return {
    plugins: [react(), tailwindcss()],
    server: { port: 5173 },
    // One page with React Flow and Framer Motion is ~180 kB gzipped; splitting it buys nothing here.
    build: { chunkSizeWarningLimit: 700 },
    test: {
      // The tested logic is pure TypeScript; no DOM needed.
      environment: 'node',
      include: ['src/**/*.test.ts'],
    },
  }
})
