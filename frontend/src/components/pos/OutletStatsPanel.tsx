import { useState } from 'react';
import {
  ArrowLeft, ShoppingBag, DollarSign, BarChart3, TrendingUp,
  Clock, CheckCircle2, XCircle, Activity,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { mockOutletStats } from '@/data/mock-pos-extended';
import { RouteUnavailableBanner, RouteGapChip } from '@/components/pos/PlatformGapStates';

interface Props {
  onBack: () => void;
  gatewayAvailable?: boolean;
}

export function OutletStatsPanel({ onBack, gatewayAvailable = false }: Props) {
  const stats = mockOutletStats;

  return (
    <div className="p-6 space-y-5 animate-fade-in">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <button onClick={onBack} className="text-muted-foreground hover:text-foreground transition-colors">
            <ArrowLeft className="h-4 w-4" />
          </button>
          <div>
            <h2 className="text-lg font-semibold text-foreground">Outlet Today</h2>
            <p className="text-xs text-muted-foreground">{stats.businessDate} — Downtown Flagship</p>
          </div>
        </div>
        {!gatewayAvailable && <RouteGapChip label="Gateway pending" />}
      </div>

      {!gatewayAvailable && (
        <RouteUnavailableBanner
          title="Outlet Statistics"
          subtitle="POS outlet stats and analytics APIs are implemented in the backend source but are not yet exposed through the gateway routing layer."
          routePath="/api/pos-stats/**"
          missingPermissions={['pos.stats.read']}
        />
      )}

      {/* Show mock-powered preview regardless */}
      <div className={cn(!gatewayAvailable && 'opacity-60 pointer-events-none')}>
        {/* KPI row */}
        <div className="grid grid-cols-2 md:grid-cols-5 gap-3 mb-5">
          {[
            { label: 'Orders Today', value: stats.ordersToday, icon: ShoppingBag, color: 'text-primary' },
            { label: 'Completed', value: stats.completedSales, icon: CheckCircle2, color: 'text-success' },
            { label: 'Revenue', value: `$${stats.revenueToday.toLocaleString()}`, icon: DollarSign, color: 'text-foreground' },
            { label: 'Avg Order', value: `$${stats.averageOrderValue.toFixed(2)}`, icon: BarChart3, color: 'text-foreground' },
            { label: 'Cancelled', value: stats.cancelledOrders, icon: XCircle, color: 'text-destructive' },
          ].map(kpi => (
            <div key={kpi.label} className="surface-elevated p-4">
              <div className="flex items-center gap-1.5 mb-2">
                <kpi.icon className="h-3.5 w-3.5 text-muted-foreground" />
                <span className="text-[10px] font-medium text-muted-foreground uppercase tracking-wide">{kpi.label}</span>
              </div>
              <p className={cn('text-xl font-semibold', kpi.color)}>{kpi.value}</p>
            </div>
          ))}
        </div>

        {/* Session status + peak info */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3 mb-5">
          <div className="surface-elevated p-4">
            <h4 className="text-xs font-semibold text-foreground mb-3 flex items-center gap-1.5">
              <Activity className="h-3.5 w-3.5" /> Active Session
            </h4>
            {stats.activeSessionCode ? (
              <div className="flex items-center gap-3">
                <span className="h-2 w-2 rounded-full bg-success animate-pulse" />
                <div>
                  <p className="text-sm font-medium text-foreground">{stats.activeSessionCode}</p>
                  <p className="text-[10px] text-muted-foreground capitalize">{stats.activeSessionStatus}</p>
                </div>
              </div>
            ) : (
              <p className="text-xs text-muted-foreground">No active session</p>
            )}
          </div>
          <div className="surface-elevated p-4">
            <h4 className="text-xs font-semibold text-foreground mb-3 flex items-center gap-1.5">
              <TrendingUp className="h-3.5 w-3.5" /> Today's Peak
            </h4>
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-foreground">{stats.peakHour}</p>
                <p className="text-[10px] text-muted-foreground">Peak revenue hour</p>
              </div>
              <div className="text-right">
                <p className="text-sm font-medium text-foreground">{stats.topCategory}</p>
                <p className="text-[10px] text-muted-foreground">Top category</p>
              </div>
            </div>
          </div>
        </div>

        {/* Hourly revenue bar chart */}
        <div className="surface-elevated p-4">
          <h4 className="text-xs font-semibold text-foreground mb-4">Hourly Revenue</h4>
          <div className="flex items-end gap-1.5 h-[120px]">
            {stats.hourlyRevenue.map(hr => {
              const max = Math.max(...stats.hourlyRevenue.map(h => h.revenue));
              const pct = max > 0 ? (hr.revenue / max) * 100 : 0;
              return (
                <div key={hr.hour} className="flex-1 flex flex-col items-center gap-1">
                  <div
                    className="w-full rounded-t bg-primary/20 hover:bg-primary/40 transition-colors relative group"
                    style={{ height: `${Math.max(pct, 4)}%` }}
                  >
                    <div className="absolute -top-6 left-1/2 -translate-x-1/2 bg-foreground text-background text-[9px] px-1.5 py-0.5 rounded opacity-0 group-hover:opacity-100 transition-opacity whitespace-nowrap">
                      ${hr.revenue}
                    </div>
                  </div>
                  <span className="text-[8px] text-muted-foreground">{hr.hour.slice(0, 2)}</span>
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {!gatewayAvailable && (
        <p className="text-[10px] text-muted-foreground text-center italic">
          Displaying mock preview data — live data requires gateway route activation
        </p>
      )}
    </div>
  );
}
