import type { PropsWithChildren } from 'react'
import { useEffect } from 'react'
import { rehydrateSession } from '@core/auth/session.service'

export function AuthProvider({ children }: PropsWithChildren) {
  useEffect(() => {
    void rehydrateSession()
  }, [])

  return children
}
