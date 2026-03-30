import { QueryClient } from '@tanstack/react-query'

const activeQueryClients = new Set<QueryClient>()

export function createTestQueryClient() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        gcTime: 0,
        refetchOnWindowFocus: false,
        retry: false,
        staleTime: 0,
      },
      mutations: {
        retry: false,
      },
    },
  })

  activeQueryClients.add(queryClient)
  return queryClient
}

export function destroyTestQueryClients() {
  for (const queryClient of activeQueryClients) {
    queryClient.getMutationCache().clear()
    queryClient.clear()
    queryClient.unmount()
  }
  activeQueryClients.clear()
}
