import type { ReactNode } from 'react'
import { Button } from '../components/button'

interface ConfirmActionDialogProps {
  cancelLabel?: string
  confirmLabel?: string
  danger?: boolean
  description: ReactNode
  onCancel: () => void
  onConfirm: () => void
  open: boolean
  title: string
}

export function ConfirmActionDialog({
  cancelLabel = 'Cancel',
  confirmLabel = 'Confirm',
  danger = false,
  description,
  onCancel,
  onConfirm,
  open,
  title,
}: ConfirmActionDialogProps) {
  if (!open) {
    return null
  }

  return (
    <div aria-modal="true" className="dialog-backdrop" role="dialog">
      <div className="dialog-panel surface-panel">
        <div className="page-stack">
          <div>
            <p className="eyebrow">{danger ? 'Destructive action' : 'Confirmation required'}</p>
            <h2>{title}</h2>
          </div>
          <div className="muted-text">{description}</div>
          <div className="form-actions">
            <Button onClick={onCancel} variant="secondary">
              {cancelLabel}
            </Button>
            <Button onClick={onConfirm} variant={danger ? 'danger' : 'primary'}>
              {confirmLabel}
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}
