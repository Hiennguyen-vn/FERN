import type { PropsWithChildren, ReactElement } from 'react'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClientProvider, type QueryClient } from '@tanstack/react-query'
import { createTestQueryClient } from './mockQueryClient'

interface RenderWithProvidersOptions {
  queryClient?: QueryClient
  route?: string
}

export function renderWithProviders(ui: ReactElement, options: RenderWithProvidersOptions = {}) {
  const queryClient = options.queryClient ?? createTestQueryClient()
  const route = options.route ?? '/'

  function Wrapper({ children }: PropsWithChildren) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter>
      </QueryClientProvider>
    )
  }

  return {
    queryClient,
    ...render(ui, { wrapper: Wrapper }),
  }
}
