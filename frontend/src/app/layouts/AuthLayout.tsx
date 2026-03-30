import type { PropsWithChildren } from 'react'

export function AuthLayout({ children }: PropsWithChildren) {
  return (
    <div className="screen-center auth-shell">
      <div className="auth-card">{children}</div>
    </div>
  )
}
