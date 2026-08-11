/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/actuator': 'http://localhost:8080',
    },
    fs: {
      // The /track bootstrap has exactly one copy and it lives beside the server that inlines it,
      // so its test has to read across the web/server boundary. Nothing is newly exposed: that file
      // is the script every visitor of /track already receives inline.
      allow: ['.', '../server/src/main/resources/tracking'],
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/setupTests.ts'],
  },
})
