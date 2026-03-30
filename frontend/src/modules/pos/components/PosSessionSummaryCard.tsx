import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Button, Card, StatusBadge } from '@design-system/index'
import { formatDateTime, formatMoney } from '@shared/formatters'
import type { PosSession } from '../model/pos.types'

interface PosSessionSummaryCardProps {
  actions?: ReactNode
  session: PosSession
}

export function PosSessionSummaryCard({ actions, session }: PosSessionSummaryCardProps) {
  return (
    <Card className="pos-session-card" title="Current session">
      <div className="page-stack">
        <div className="page-header">
          <div className="stack-inline">
            <strong>{session.sessionCode}</strong>
            <StatusBadge status={session.status} />
          </div>
          <div className="form-actions align-start">
            <Button asChild size="sm" variant="secondary">
              <Link to={`/pos/sessions/${session.id}`}>View detail</Link>
            </Button>
            {actions}
          </div>
        </div>
        <div className="pos-summary-grid">
          <span>Outlet #{session.outletId}</span>
          <span>Region #{session.regionId}</span>
          <span>Business date: {session.businessDate}</span>
          <span>Opened: {formatDateTime(session.openedAt)}</span>
          <span>Expected cash: {formatMoney(session.expectedCashAmount, session.currencyCode)}</span>
          <span>Discrepancy: {formatMoney(session.discrepancyAmount, session.currencyCode)}</span>
        </div>
        {session.note ? <p className="muted-text">{session.note}</p> : null}
      </div>
    </Card>
  )
}
