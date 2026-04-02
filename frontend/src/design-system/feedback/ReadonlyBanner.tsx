import clsx from 'clsx'
import { AppIcon } from '@app/components/AppIcon'

interface ReadonlyBannerProps {
  icon?: string
  label?: string
  message?: string
  title?: string
  tone?: 'info' | 'warning' | 'danger'
}

export function ReadonlyBanner({
  icon = 'lock',
  label,
  message = 'This surface is currently in read-only mode.',
  title = 'Read-only surface',
  tone = 'info',
}: ReadonlyBannerProps) {
  return (
    <div className={clsx('record-state-banner', `record-state-banner-${tone}`)} role="status">
      <div className="record-state-banner-content">
        <div className="record-state-banner-icon">
          <AppIcon filled name={icon} />
        </div>
        <div className="record-state-banner-copy">
          <strong>{title}</strong>
          <span>{message}</span>
        </div>
      </div>
      {label ? <span className="record-state-banner-label">{label}</span> : null}
    </div>
  )
}
