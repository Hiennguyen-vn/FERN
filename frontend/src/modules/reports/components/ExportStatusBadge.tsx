import { StatusBadge } from '@design-system/index'

export function ExportStatusBadge({ status }: { status: string }) {
  return <StatusBadge status={status} />
}
