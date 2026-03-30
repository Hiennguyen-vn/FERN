import type { ReactNode } from 'react'
import { Button } from '../button'

interface ErrorStateProps {
  actionLabel?: string
  message: string
  onAction?: () => void
  title?: string
  children?: ReactNode
}

export function ErrorState({ actionLabel, children, message, onAction, title = 'Error' }: ErrorStateProps) {
  return (
    <div className="surface-panel error-panel state-panel state-panel-error">
      <div className="page-stack">
        <div className="state-panel-heading">
          <span className="state-panel-kicker">Attention required</span>
          <h2>{title}</h2>
        </div>
        <p>{message}</p>
        {children}
        {actionLabel && onAction ? (
          <div className="form-actions align-start">
            <Button onClick={onAction} size="sm" variant="secondary">
              {actionLabel}
            </Button>
          </div>
        ) : null}
      </div>
    </div>
  )
}
