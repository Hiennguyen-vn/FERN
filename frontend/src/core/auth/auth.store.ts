import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'
import type { AuthState } from './auth.types'

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      isAuthenticated: false,
      principal: null,
      user: null,
      accessToken: null,
      refreshToken: null,
      expiresAt: null,

      setSession: (session, principal) =>
        set({
          isAuthenticated: true,
          principal,
          user: session.user,
          accessToken: session.accessToken,
          refreshToken: session.refreshToken,
          expiresAt: session.expiresAt,
        }),

      clearSession: () =>
        set({
          isAuthenticated: false,
          principal: null,
          refreshToken: null,
          expiresAt: null,
          accessToken: null,
          user: null,
        }),

      setAccessToken: (token, principal, expiresAt) =>
        set({
          isAuthenticated: true,
          principal,
          accessToken: token,
          expiresAt: expiresAt ?? null,
        }),
    }),
    {
      name: 'fern_auth_storage',
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({
        isAuthenticated: state.isAuthenticated,
        principal: state.principal,
        user: state.user,
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
        expiresAt: state.expiresAt,
      }),
    },
  ),
)
