import type { ButtonHTMLAttributes, ReactElement } from 'react'
import { cloneElement, isValidElement } from 'react'
import clsx from 'clsx'

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'
type ButtonSize = 'sm' | 'md' | 'lg'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  asChild?: boolean
  loading?: boolean
  variant?: ButtonVariant
  size?: ButtonSize
}

export function Button({
  asChild,
  children,
  className,
  disabled,
  loading = false,
  size = 'md',
  type = 'button',
  variant = 'primary',
  ...props
}: ButtonProps) {
  const classes = clsx('button', `button-${variant}`, `button-${size}`, className)

  if (asChild && isValidElement(children)) {
    return cloneElement(children, {
      className: clsx(classes, (children.props as { className?: string }).className),
    } as Partial<ReactElement['props']>)
  }

  return (
    <button className={classes} disabled={disabled || loading} type={type} {...props}>
      {loading ? 'Loading...' : children}
    </button>
  )
}
