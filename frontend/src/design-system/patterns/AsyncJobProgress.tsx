import { Button } from '../components/button'
import { Card } from '../components/card'
import { StatusBadge } from './StatusBadge'

interface AsyncJobProgressProps {
  actionLabel?: string
  completedAt?: string | null
  description?: string | null
  onAction?: () => void
  requestedAt?: string | null
  status: string
  title: string
}

export function AsyncJobProgress({
  actionLabel,
  completedAt,
  description,
  onAction,
  requestedAt,
  status,
  title,
}: AsyncJobProgressProps) {
  return (
    <Card title={title}>
      <div className="page-stack">
        <StatusBadge status={status} />
        {description ? <p className="muted-text">{description}</p> : null}
        {requestedAt ? <p className="muted-text">Requested: {new Date(requestedAt).toLocaleString()}</p> : null}
        {completedAt ? <p className="muted-text">Completed: {new Date(completedAt).toLocaleString()}</p> : null}
        {actionLabel && onAction ? (
          <div>
            <Button onClick={onAction} size="sm" variant="secondary">
              {actionLabel}
            </Button>
          </div>
        ) : null}
      </div>
    </Card>
  )
}
