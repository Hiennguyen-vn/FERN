import { useEffect, useRef } from 'react'
import { useAuthStore } from '@core/auth/auth.store'
import { refreshAccessToken } from '@core/auth/refresh.service'

const TAB_FOCUS_REFRESH_THRESHOLD_MS = 5 * 60 * 1000 // 5 phút

/**
 * useSessionGuard
 *
 * L-01: Frontend mitigation cho mid-session token revocation.
 *
 * Khi user quay lại tab sau ≥5 phút, hook tự động refresh access token.
 * Nếu refresh phát hiện policy/scope version giảm (revocation), refresh.service
 * sẽ redirect tới /session-expired và clear session.
 *
 * Backend gap: revocation events proactive (WebSocket/SSE) chưa có.
 * Hook này giảm thiểu stale-session window từ "full token TTL" xuống "5 phút".
 *
 * Không làm gì nếu user chưa authenticated (prevents unnecessary calls).
 */
export function useSessionGuard(): void {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  const lastActiveRef = useRef<number>(Date.now())

  useEffect(() => {
    if (!isAuthenticated) {
      return
    }

    function onVisibilityChange() {
      if (document.visibilityState !== 'visible') {
        lastActiveRef.current = Date.now()
        return
      }

      const hiddenDurationMs = Date.now() - lastActiveRef.current
      if (hiddenDurationMs < TAB_FOCUS_REFRESH_THRESHOLD_MS) {
        return
      }

      // Tab was inactive for ≥5 minutes — proactively refresh to detect revocation.
      refreshAccessToken().catch(() => {
        // refreshAccessToken handles redirect on hard failure;
        // soft errors (network) are silently ignored here.
      })
    }

    document.addEventListener('visibilitychange', onVisibilityChange)
    return () => {
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
  }, [isAuthenticated])
}
