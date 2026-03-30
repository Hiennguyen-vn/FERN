import { useAuthStore } from './auth.store'

export const usePrincipal = () => useAuthStore((state) => state.principal)
export const useIsAuthenticated = () => useAuthStore((state) => state.isAuthenticated)
