import { useQuery } from '@tanstack/react-query'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useScopeContext } from '@core/scopes/useScopeContext'
import { buildFallbackActionHub, buildFallbackShellContext, uiApi } from './ui.api'

export function useActionHub() {
  const principal = usePrincipal()
  const { selectedOutletId, selectedRegionId } = useScopeContext()
  const fallback = buildFallbackActionHub({ principal, selectedOutletId, selectedRegionId })

  return useQuery({
    queryKey: ['ui', 'action-hub', principal?.username ?? 'guest', selectedRegionId ?? 'none', selectedOutletId ?? 'none'],
    queryFn: async () => {
      try {
        return await uiApi.getActionHub({ selectedOutletId, selectedRegionId })
      } catch {
        return fallback
      }
    },
    initialData: fallback,
    placeholderData: fallback,
    staleTime: 60_000,
  })
}

export function useShellContext() {
  const principal = usePrincipal()
  const { selectedOutletId, selectedRegionId } = useScopeContext()
  const fallback = buildFallbackShellContext({ principal, selectedOutletId, selectedRegionId })

  return useQuery({
    queryKey: ['ui', 'shell-context', principal?.username ?? 'guest', selectedRegionId ?? 'none', selectedOutletId ?? 'none'],
    queryFn: async () => {
      try {
        return await uiApi.getShellContext({ selectedOutletId, selectedRegionId })
      } catch {
        return fallback
      }
    },
    initialData: fallback,
    placeholderData: fallback,
    staleTime: 60_000,
  })
}
