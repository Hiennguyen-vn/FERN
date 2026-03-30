import type { ReactNode } from 'react'

interface FormActionsProps {
  primaryAction: ReactNode
  secondaryAction?: ReactNode
}

export function FormActions({ primaryAction, secondaryAction }: FormActionsProps) {
  return (
    <div className="form-actions">
      {secondaryAction}
      {primaryAction}
    </div>
  )
}
