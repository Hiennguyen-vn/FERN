import type { ReactNode } from 'react'
import { Button } from '../button'

interface EmptyStateProps {
  actionLabel?: string
  description: string
  onAction?: () => void
  title: string
  children?: ReactNode
}

export function EmptyState({ actionLabel, children, description, onAction, title }: EmptyStateProps) {
  return (
    <div className="surface-panel state-panel state-panel-empty">
      <div className="page-stack">
        <div className="state-panel-heading">
          <span className="state-panel-kicker">Empty state</span>
          <h2>{title}</h2>
        </div>
        <p className="muted-text">{description}</p>
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
