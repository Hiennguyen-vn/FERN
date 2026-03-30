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

function getDisplayValue(mode: MaskedMode, value: ReactNode) {
  switch (mode) {
    case 'hidden':
      return 'Hidden by policy'
    case 'masked':
      return '••••••••'
    case 'readonly-visible':
    case 'fully-visible':
    default:
      return value
  }
}

export function MaskedField({
  className,
  helperText,
  label,
  mode = 'fully-visible',
  value = '—',
}: MaskedFieldProps) {
  return (
    <div className={clsx('masked-field surface-panel', className)}>
      <span className="field-label">{label}</span>
      <strong className={clsx('masked-field-value', mode !== 'fully-visible' && mode !== 'readonly-visible' && 'muted-text')}>
        {getDisplayValue(mode, value)}
      </strong>
      {helperText ? <span className="muted-text">{helperText}</span> : null}
    </div>
  )
}
