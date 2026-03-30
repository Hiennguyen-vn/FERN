export const env = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? (import.meta.env.DEV ? '' : '/api'),
  appName: import.meta.env.VITE_APP_NAME ?? 'FERN Platform',
}
