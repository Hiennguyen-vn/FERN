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
      //
      // IMPORTANT — do not use SPA-first segments as prefixes:
      //   - `/pos`, `/finance`, `/inventory`, `/reports`, `/audit` are React routes; proxying them
      //     breaks deep links and refresh (browser gets gateway 404 JSON instead of index.html).
      // Use concrete gateway paths instead (e.g. `pos-sessions`, not `pos`; `finance-config`, not `finance`).
      // Keep in sync with `dev-server.mjs` `gatewayProxyPrefixes` when adding new APIs.
      // Aligned with `dev-server.mjs` `gatewayProxyPrefixes` (+ `/exchange-rates` for org rates API).
      // Never use SPA-first segments here (`pos`, `finance`, `inventory`, `reports`, …) — those must serve `index.html`.
      '^/(?:actuator|auth|attendance-approvals|attendance-events|audit|catalog/promotions|employee-assignments|employee-contracts|employees|exchange-rates|finance-config|goods-receipts|ingredient-categories|ingredients|inventory-transactions|outlets|payroll-periods|payroll-runs|permissions|pos-sessions|product-availability|product-categories|product-prices|products|purchase-orders|recipe-versions|recipes|regions|reports/exports|reports/payroll/runs|reports/payroll/summary|roles|sale-orders|shift-assignments|shift-schedules|stock-adjustments|stock-balances|stock-count-sessions|supplier-invoices|supplier-payments|suppliers|tax-rates|ui|units-of-measure|uom-conversions|users|waste-records|ws)': {
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
      ],
    },
  },
})
