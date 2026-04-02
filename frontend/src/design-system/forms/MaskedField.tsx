import type { ReactNode } from 'react'
import clsx from 'clsx'

type MaskedMode = 'hidden' | 'masked' | 'readonly-visible' | 'fully-visible'

interface MaskedFieldProps {
  className?: string
  helperText?: string
  label: string
  mode?: MaskedMode
  value?: ReactNode
}

export function MaskedField({
  className,
  helperText,
  label,
  mode = 'fully-visible',
  value = '—',
}: MaskedFieldProps) {
  const isMasked = mode === 'masked'
  const isHidden = mode === 'hidden'

  return (
    <div className={clsx('masked-field surface-panel', className)}>
      <span className="field-label">{label}</span>
      {isHidden ? (
        <span className="muted-text" style={{ fontStyle: 'italic', fontSize: '0.875rem' }}>
          Hidden by policy
        </span>
      ) : (
        <strong
          className="masked-field-value"
          style={isMasked ? { filter: 'blur(4px)', userSelect: 'none', pointerEvents: 'none' } : undefined}
          aria-hidden={isMasked}
        >
          {value}
        </strong>
      )}
      {helperText ? <span className="muted-text" style={{ fontSize: '0.78rem' }}>{helperText}</span> : null}
    </div>
  )
}
