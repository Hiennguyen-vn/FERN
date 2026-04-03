import { Link } from 'react-router-dom'
import clsx from 'clsx'
import { AppIcon } from '@app/components/AppIcon'
import { useActionHub } from '@core/api/ui.hooks'
import { useScopeContext } from '@core/scopes/useScopeContext'
import { Badge, Button, ErrorState, ReadonlyBanner } from '@design-system/index'

const PERSONA_LABELS = {
  system_admin: 'System Admin',
  finance: 'Finance View',
  outlet_manager: 'Outlet Manager',
  staff: 'Staff Workspace',
  operations: 'Operations View',
} as const

export function HomeActionHub() {
  const { selectedOutletId, selectedRegionId } = useScopeContext()
  const actionHubQuery = useActionHub()

  if (actionHubQuery.error && !actionHubQuery.data) {
    return (
      <ErrorState
        actionLabel="Retry"
        message="Unable to assemble the action hub for the current role. Retry after the shell context settles."
        onAction={() => void actionHubQuery.refetch()}
        title="Action hub unavailable"
      />
    )
  }

  const actionHub = actionHubQuery.data
  if (!actionHub) {
    return null
  }

  const activityFeed = [
    ...actionHub.queues.map((queue) => ({
      id: `queue-${queue.id}`,
      title: queue.title,
      message: queue.description,
      tone: queue.tone,
      count: queue.count,
      href: queue.href,
    })),
    ...actionHub.alerts.map((alert) => ({
      id: `alert-${alert.id}`,
      title: alert.title,
      message: alert.message,
      tone: alert.tone,
      count: null,
      href: alert.href,
    })),
  ].slice(0, 6)
  const prioritySignals = actionHub.alerts.slice(0, 3)

  return (
    <section className="page-stack action-hub">
      {!selectedRegionId && !selectedOutletId ? (
        <ReadonlyBanner message="Select a region or outlet in the shell to narrow the operational context before acting." />
      ) : null}

      <section className="action-hub-stage surface-panel">
        <div className="page-heading action-hub-stage-copy">
          <p className="workspace-breadcrumb">Home / Action Hub</p>
          <div className="action-hub-stage-meta">
            <span className="action-hub-persona-badge">{PERSONA_LABELS[actionHub.persona]}</span>
            {actionHub.scopeSummary.chips.slice(0, 2).map((chip) => (
              <span className="meta-chip" key={chip}>
                {chip}
              </span>
            ))}
          </div>
          <h1>Action Hub</h1>
          <p className="muted-text">
            Operational overview for <strong>{PERSONA_LABELS[actionHub.persona]}</strong>, with the current scope pinned before you act.
          </p>
        </div>
        <div className="action-hub-stage-aside">
          <div className="action-hub-stage-summary">
            <p className="eyebrow">Working scope</p>
            <strong className="action-summary-title">{actionHub.scopeSummary.title}</strong>
            <p className="muted-text">{actionHub.scopeSummary.subtitle}</p>
            <div className="scope-summary-grid">
              {actionHub.scopeSummary.chips.map((chip) => (
                <span className="meta-chip" key={chip}>
                  {chip}
                </span>
              ))}
            </div>
          </div>
          <div className="action-hub-stage-cta">
            {actionHub.quickActions.slice(0, 2).map((action, index) => (
              <Button
                asChild
                key={action.id}
                size="sm"
                variant={action.tone === 'primary' ? 'primary' : 'secondary'}
              >
                <Link to={action.href}>
                  <AppIcon name={index === 0 ? 'add' : 'arrow_forward'} size="sm" />
                  {index === 0 ? `Open ${action.title}` : `Review ${action.title}`}
                </Link>
              </Button>
            ))}
          </div>
        </div>
      </section>

      <section className="action-hub-kpi-grid">
        {actionHub.kpis.map((kpi) => (
          <article className={clsx('action-kpi-card', `action-kpi-${kpi.tone}`)} key={kpi.id}>
            <div className="action-kpi-topline">
              <span className="action-kpi-icon">
                <AppIcon
                  name={
                    kpi.tone === 'success'
                      ? 'trending_up'
                      : kpi.tone === 'warning'
                        ? 'warning'
                        : kpi.tone === 'danger'
                          ? 'shield'
                          : 'dashboard'
                  }
                  size="sm"
                />
              </span>
              <span className="action-kpi-label">{kpi.label}</span>
            </div>
            <strong className="action-kpi-value">{kpi.value}</strong>
            {kpi.detail ? <p className="muted-text">{kpi.detail}</p> : null}
          </article>
        ))}
      </section>

      <section className="action-hub-main-grid">
        <section className="surface-panel action-hub-panel action-hub-feed-panel">
          <div className="action-hub-panel-heading">
            <div>
              <p className="eyebrow">Operational pulse</p>
              <h2>Queues and alerts</h2>
            </div>
          </div>
          <div className="action-timeline">
            {activityFeed.length > 0 ? (
              activityFeed.map((item, index) => (
                <div className="action-timeline-item" key={item.id}>
                  <div className="action-timeline-rail" aria-hidden="true">
                    <span className={clsx('action-timeline-dot', `action-timeline-dot-${item.tone}`)} />
                    {index < activityFeed.length - 1 ? <span className="action-timeline-line" /> : null}
                  </div>
                  <div className="action-timeline-content">
                    <div className="action-timeline-head">
                      <strong>{item.title}</strong>
                      {item.count !== null ? (
                        <Badge tone={item.tone === 'primary' ? 'neutral' : item.tone}>{item.count}</Badge>
                      ) : null}
                    </div>
                    <p className="muted-text">{item.message}</p>
                    {item.href ? (
                      <Button asChild size="sm" variant="ghost">
                        <Link to={item.href}>
                          <AppIcon name="open_in_new" size="sm" />
                          Open
                        </Link>
                      </Button>
                    ) : null}
                  </div>
                </div>
              ))
            ) : (
              <div className="action-hub-empty">
                <p className="muted-text">No operational queues are exposed for the current role.</p>
              </div>
            )}
          </div>
        </section>

        <section className="surface-panel action-hub-panel action-hub-glass-panel">
          <div className="action-hub-panel-heading">
            <div>
              <p className="eyebrow">Priority signals</p>
              <h2>Focus next</h2>
            </div>
          </div>
          {prioritySignals.length > 0 ? (
            <div className="action-hub-signal-list">
              {prioritySignals.map((alert) => (
                <div className={clsx('action-hub-signal-row', `action-hub-signal-${alert.tone}`)} key={alert.id}>
                  <div className="action-hub-signal-copy">
                    <strong>{alert.title}</strong>
                    <p className="muted-text">{alert.message}</p>
                  </div>
                  {alert.href ? (
                    <Button asChild size="sm" variant="ghost">
                      <Link to={alert.href}>
                        <AppIcon name="open_in_new" size="sm" />
                        Open
                      </Link>
                    </Button>
                  ) : null}
                </div>
              ))}
            </div>
          ) : (
            <div className="action-hub-empty">
              <p className="muted-text">No critical alerts are active for the current role and scope.</p>
            </div>
          )}
        </section>

        <section className="surface-panel action-hub-panel action-hub-quick-panel">
          <div className="action-hub-panel-heading">
            <div>
              <p className="eyebrow">Command shortcuts</p>
              <h2>Quick actions</h2>
            </div>
          </div>
          <div className="action-list">
            {actionHub.quickActions.map((action) => (
              <Button
                asChild
                className={clsx('action-hub-quick-button', `action-tone-${action.tone}`)}
                key={action.id}
                variant={action.tone === 'primary' ? 'primary' : 'secondary'}
              >
                <Link to={action.href}>
                  <span className="action-hub-quick-copy">
                    <strong>{action.title}</strong>
                    <span>{action.description}</span>
                  </span>
                  <AppIcon name="arrow_forward" size="sm" />
                </Link>
              </Button>
            ))}
          </div>
        </section>
      </section>

      <section className="page-stack action-hub-atlas">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Module atlas</p>
            <h2>Published workspaces</h2>
          </div>
        </div>
        <div className="action-hub-atlas-list">
          {actionHub.modules.map((moduleEntry) => (
            <Link className="action-hub-atlas-row" key={moduleEntry.id} to={moduleEntry.href}>
              <div className="action-hub-atlas-copy">
                <strong>{moduleEntry.title}</strong>
                <p className="muted-text">{moduleEntry.description}</p>
              </div>
              <span className="action-hub-atlas-open">
                <AppIcon name="arrow_outward" size="sm" />
              </span>
            </Link>
          ))}
        </div>
      </section>
    </section>
  )
}
