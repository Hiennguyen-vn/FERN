import { QueryClient } from '@tanstack/react-query'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5, // 5 minutes by default
      gcTime: 1000 * 60 * 30, // 30 minutes (formerly cacheTime)
      retry: (failureCount, error: any) => {
        // Don't retry on 401/403 or 404
        if (error?.status === 401 || error?.status === 403 || error?.status === 404) {
          return false
        }
        return failureCount < 3
      },
      refetchOnWindowFocus: false, // Too noisy for enterprise apps, enable per-query if needed
    },
    mutations: {
      retry: false, // Never retry mutations automatically
    },
  },
})
