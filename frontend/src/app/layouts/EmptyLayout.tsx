import type { PropsWithChildren } from 'react'

export function EmptyLayout({ children }: PropsWithChildren) {
  return <div className="surface-page">{children}</div>
}
