import type { InputHTMLAttributes } from 'react'
import clsx from 'clsx'

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  error?: string
  label?: string
}

export function Input({ className, error, id, label, ...props }: InputProps) {
  const inputId = id ?? props.name ?? label?.toLowerCase().replace(/\s+/g, '-')

  return (
    <label className="field" htmlFor={inputId}>
      {label ? <span className="field-label">{label}</span> : null}
      <input className={clsx('input', error && 'input-error', className)} id={inputId} {...props} />
      {error ? <span className="error-text">{error}</span> : null}
    </label>
  )
}
