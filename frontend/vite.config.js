/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';
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
});
