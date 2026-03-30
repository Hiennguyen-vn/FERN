import { Badge } from '../components/badge'

type StatusTone = 'neutral' | 'success' | 'warning' | 'danger'

function toneForStatus(status: string): StatusTone {
  switch (status.toUpperCase()) {
    case 'COMPLETED':
    case 'SUCCESS':
    case 'APPROVED':
      return 'success'
    case 'QUEUED':
    case 'RUNNING':
    case 'PENDING':
      return 'warning'
    case 'FAILED':
    case 'REJECTED':
    case 'CANCELLED':
      return 'danger'
    default:
      return 'neutral'
  }
}

export function StatusBadge({ status }: { status: string }) {
  return <Badge tone={toneForStatus(status)}>{status.replace(/_/g, ' ')}</Badge>
}
