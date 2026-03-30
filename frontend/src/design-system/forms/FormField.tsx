import type { PropsWithChildren, ReactNode } from 'react'
import clsx from 'clsx'

interface FormFieldProps extends PropsWithChildren {
  error?: string
  helperText?: string
  label?: ReactNode
  readOnly?: boolean
  required?: boolean
}

export function FormField({ children, error, helperText, label, readOnly, required }: FormFieldProps) {
  return (
    <div className={clsx('field', error && 'field-error')}>
      {label ? (
        <div className="field-label-row">
          <span className="field-label">
            {label}
            {required ? <span aria-hidden="true"> *</span> : null}
          </span>
          {readOnly ? <span className="muted-text">Read only</span> : null}
        </div>
      ) : null}
      {children}
      {error ? <span className="error-text">{error}</span> : helperText ? <span className="muted-text">{helperText}</span> : null}
    </div>
  )
}
