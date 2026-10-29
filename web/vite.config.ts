import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: { port: 5173 },
  test: {
    // The tested logic is pure TypeScript; no DOM needed.
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
})
