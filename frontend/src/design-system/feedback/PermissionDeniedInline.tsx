import { AppIcon } from '@app/components/AppIcon'

interface PermissionDeniedInlineProps {
  message?: string
  title?: string
}

export function PermissionDeniedInline({
  message = 'You do not have permission to perform this action in the current scope, state, or role.',
  title = 'Permission denied',
}: PermissionDeniedInlineProps) {
  return (
    <div className="record-state-banner record-state-banner-danger" role="status">
      <div className="record-state-banner-content">
        <div className="record-state-banner-icon">
          <AppIcon filled name="lock" />
        </div>
        <div className="record-state-banner-copy">
          <strong>{title}</strong>
          <span>{message}</span>
        </div>
      </div>
      <span className="record-state-banner-label">Access restricted</span>
    </div>
  )
}
