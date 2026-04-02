import type { HTMLAttributes } from 'react'
import clsx from 'clsx'

interface AppIconProps extends HTMLAttributes<HTMLSpanElement> {
  filled?: boolean
  name: string
  size?: 'sm' | 'md' | 'lg'
}

export function AppIcon({ className, filled = false, name, size = 'md', ...props }: AppIconProps) {
  return (
    <span
      aria-hidden="true"
      className={clsx('material-symbols-outlined', 'app-icon', `app-icon-${size}`, filled && 'is-filled', className)}
      {...props}
    >
      {name}
    </span>
  )
}
