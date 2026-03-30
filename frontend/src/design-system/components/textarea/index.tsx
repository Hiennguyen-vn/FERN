import type { TextareaHTMLAttributes } from 'react'
import clsx from 'clsx'

interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  error?: string
  label?: string
}

export function Textarea({ className, error, id, label, ...props }: TextareaProps) {
  const textareaId = id ?? props.name ?? label?.toLowerCase().replace(/\s+/g, '-')

  return (
    <label className="field" htmlFor={textareaId}>
      {label ? <span className="field-label">{label}</span> : null}
      <textarea className={clsx('input', 'textarea', error && 'input-error', className)} id={textareaId} {...props} />
      {error ? <span className="error-text">{error}</span> : null}
    </label>
  )
}
