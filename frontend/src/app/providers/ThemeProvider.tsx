import type { PropsWithChildren } from 'react'

export function ThemeProvider({ children }: PropsWithChildren) {
  return <div data-theme="fern">{children}</div>
}
