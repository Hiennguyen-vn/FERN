/// <reference types="vitest" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { resolve } from 'path'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@app': resolve(__dirname, 'src/app'),
      '@core': resolve(__dirname, 'src/core'),
      '@design-system': resolve(__dirname, 'src/design-system'),
      '@shared': resolve(__dirname, 'src/shared'),
      '@modules': resolve(__dirname, 'src/modules'),
      '@styles': resolve(__dirname, 'src/styles'),
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor-react': ['react', 'react-dom', 'react-router-dom'],
          'vendor-query': ['@tanstack/react-query'],
          'vendor-state': ['zustand'],
          'vendor-form': ['react-hook-form', '@hookform/resolvers', 'zod'],
          'vendor-http': ['axios'],
        },
      },
    },
  },
  server: {
    port: 3000,
    proxy: {
      // Single catch-all proxy: any path that looks like an API call
      // (starts with a known API path prefix) goes to the backend gateway on :8080.
      // The gateway is responsible for routing internally to each microservice.
      // Add new prefixes here as new backend routes are introduced.
      '^/(auth|users|roles|permissions|permission-overrides|scope-assignments|regions|outlets|audit|request-traces|security-events|products|ingredients|recipes|prices|pos|inventory|purchase-orders|goods-receipts|suppliers|invoices|payment-requests|employees|contracts|shifts|attendance-events|attendance-approvals|payroll-runs|payroll-approvals|payroll-config|reports|export-jobs|finance)': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/shared/test-utils/setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      exclude: [
        'node_modules/',
        'src/shared/test-utils/',
        '**/*.d.ts',
        '**/*.config.*',
        '**/index.ts',
      ],
    },
  },
})
