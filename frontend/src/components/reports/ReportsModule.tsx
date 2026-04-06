import { useState } from 'react';
import {
  BarChart3, TrendingUp, TrendingDown, DollarSign,
  ShoppingCart, Store, Trophy, ChevronRight,
  Layers, AlertTriangle, CheckCircle2, Info, ArrowUpRight,
  XCircle, ArrowDownRight, Loader2,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import {
  BarChart, Bar,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend,
  PieChart, Pie, Cell,
} from 'recharts';
import { useReportData, type LowStockAlert } from '@/hooks/use-dashboard-data';
import { useScopeIds } from '@/lib/scope/ScopeContext';

type ReportTab = 'revenue' | 'inventory';

const COLORS = [
  'hsl(var(--primary))',
  'hsl(var(--primary) / 0.7)',
  'hsl(var(--primary) / 0.4)',
  'hsl(var(--primary) / 0.25)',
];

export function ReportsModule() {
  const [activeTab, setActiveTab] = useState<ReportTab>('revenue');

  return (
    <div className="flex flex-col h-full animate-fade-in">
      <div className="border-b bg-card px-6 flex items-center gap-0 flex-shrink-0">
        {([
          { key: 'revenue' as ReportTab, label: 'Revenue Dashboard', icon: DollarSign },
          { key: 'inventory' as ReportTab, label: 'Inventory Health', icon: BarChart3 },
        ]).map(tab => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={cn(
              'flex items-center gap-1.5 px-4 py-3 text-xs font-medium border-b-2 transition-colors',
              activeTab === tab.key
                ? 'border-primary text-primary'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            )}
          >
            <tab.icon className="h-3.5 w-3.5" />
            {tab.label}
          </button>
        ))}
      </div>

      <div className="flex-1 overflow-y-auto">
        {activeTab === 'revenue' && <RevenueDashboard />}
        {activeTab === 'inventory' && <InventoryHealth />}
      </div>
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════════════════════ */
/* Revenue Dashboard — Real Data                                             */
/* ═══════════════════════════════════════════════════════════════════════════ */

function RevenueDashboard() {
  const { scope } = useScopeIds();
  const { outletRevenue, loading } = useReportData(scope);

  const totalRevenue = outletRevenue.reduce((s, o) => s + o.revenue, 0);
  const totalOrders = outletRevenue.reduce((s, o) => s + o.orders, 0);
  const avgAOV = totalOrders > 0 ? totalRevenue / totalOrders : 0;

  if (loading) {
    return <div className="flex items-center justify-center py-20"><Loader2 className="h-5 w-5 animate-spin text-muted-foreground" /></div>;
  }

  return (
    <div className="p-6 space-y-5 animate-fade-in">
      {/* Header */}
      <div>
        <div className="flex items-center gap-1.5 text-[11px] text-muted-foreground mb-1">
          <Layers className="h-3 w-3" />
          <span>Reports</span>
          <ChevronRight className="h-3 w-3" />
          <span className="text-foreground font-medium">Revenue</span>
        </div>
        <h2 className="text-lg font-semibold text-foreground">Revenue Dashboard</h2>
        <p className="text-xs text-muted-foreground mt-0.5">Real-time revenue data from your database</p>
      </div>

      {/* KPIs */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        {[
          { label: 'Total Revenue', value: `$${totalRevenue.toLocaleString(undefined, { minimumFractionDigits: 2 })}`, icon: DollarSign, change: totalRevenue > 0 ? '+' : '' },
          { label: 'Total Orders', value: totalOrders.toLocaleString(), icon: ShoppingCart },
          { label: 'Avg Order Value', value: `$${avgAOV.toFixed(2)}`, icon: BarChart3 },
          { label: 'Outlets', value: String(outletRevenue.length), icon: Store },
        ].map(kpi => (
          <div key={kpi.label} className="surface-elevated p-4">
            <div className="flex items-center gap-1.5 mb-2">
              <kpi.icon className="h-3.5 w-3.5 text-muted-foreground" />
              <span className="text-[10px] font-medium text-muted-foreground uppercase tracking-wide">{kpi.label}</span>
            </div>
            <p className="text-xl font-semibold text-foreground">{kpi.value}</p>
          </div>
        ))}
      </div>

      {/* Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-5 gap-5">
        {/* Revenue by Outlet Bar Chart */}
        <div className="lg:col-span-3 surface-elevated overflow-hidden">
          <div className="px-4 py-3 border-b border-border">
            <span className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wide">Revenue by Outlet</span>
          </div>
          <div className="p-4">
            {outletRevenue.length === 0 ? (
              <div className="flex items-center justify-center h-56 text-sm text-muted-foreground">No revenue data yet</div>
            ) : (
              <div className="h-56">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={outletRevenue}>
                    <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" vertical={false} />
                    <XAxis
                      dataKey="outletName"
                      tick={{ fontSize: 10, fill: 'hsl(var(--muted-foreground))' }}
                      axisLine={false}
                      tickLine={false}
                    />
                    <YAxis
                      tick={{ fontSize: 10, fill: 'hsl(var(--muted-foreground))' }}
                      tickFormatter={v => `$${v}`}
                      axisLine={false}
                      tickLine={false}
                      width={50}
                    />
                    <Tooltip
                      contentStyle={{
                        fontSize: 11, borderRadius: 8,
                        border: '1px solid hsl(var(--border))',
                        background: 'hsl(var(--card))',
                      }}
                      formatter={(v: number) => [`$${v.toFixed(2)}`, 'Revenue']}
                    />
                    <Bar dataKey="revenue" fill="hsl(var(--primary))" radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </div>
        </div>

        {/* Revenue Share Pie */}
        <div className="lg:col-span-2 surface-elevated overflow-hidden">
          <div className="px-4 py-3 border-b border-border">
            <span className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wide">Revenue Share</span>
          </div>
          <div className="p-4">
            {outletRevenue.length === 0 ? (
              <div className="flex items-center justify-center h-56 text-sm text-muted-foreground">No data</div>
            ) : (
              <div className="h-56">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={outletRevenue.map(o => ({ name: o.outletName, value: o.revenue }))}
                      cx="50%" cy="50%" innerRadius={50} outerRadius={80}
                      paddingAngle={2} dataKey="value"
                    >
                      {outletRevenue.map((_, i) => (
                        <Cell key={i} fill={COLORS[i % COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip
                      contentStyle={{
                        fontSize: 11, borderRadius: 8,
                        border: '1px solid hsl(var(--border))',
                        background: 'hsl(var(--card))',
                      }}
                      formatter={(v: number) => [`$${v.toFixed(2)}`]}
                    />
                    <Legend iconType="circle" iconSize={6} wrapperStyle={{ fontSize: 10 }} />
                  </PieChart>
                </ResponsiveContainer>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Outlet Ranking Table */}
      <div className="surface-elevated overflow-hidden">
        <div className="px-4 py-3 border-b border-border">
          <span className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wide">Outlet Performance Ranking</span>
        </div>
        <table className="w-full">
          <thead>
            <tr className="border-b bg-muted/30">
              <th className="text-[11px] font-medium text-muted-foreground px-4 py-2.5 text-center w-12">Rank</th>
              <th className="text-[11px] font-medium text-muted-foreground px-4 py-2.5 text-left">Outlet</th>
              <th className="text-[11px] font-medium text-muted-foreground px-4 py-2.5 text-right">Revenue</th>
              <th className="text-[11px] font-medium text-muted-foreground px-4 py-2.5 text-right">Orders</th>
              <th className="text-[11px] font-medium text-muted-foreground px-4 py-2.5 text-right">AOV</th>
              <th className="text-[11px] font-medium text-muted-foreground px-4 py-2.5 text-left w-28">Share</th>
            </tr>
          </thead>
          <tbody>
            {outletRevenue.length === 0 ? (
              <tr><td colSpan={6} className="px-4 py-8 text-center text-sm text-muted-foreground">No revenue data available</td></tr>
            ) : outletRevenue.map((outlet, idx) => {
              const share = totalRevenue > 0 ? (outlet.revenue / totalRevenue) * 100 : 0;
              return (
                <tr key={outlet.outletId} className="border-b last:border-0 hover:bg-muted/20 transition-colors">
                  <td className="px-4 py-3 text-center">
                    <span className={cn(
                      'inline-flex items-center justify-center h-5 w-5 rounded-full text-[10px] font-bold',
                      idx === 0 ? 'bg-warning/10 text-warning' : 'bg-muted text-muted-foreground',
                    )}>
                      {idx + 1}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <p className="text-sm font-medium text-foreground">{outlet.outletName}</p>
                    {idx === 0 && (
                      <span className="inline-flex items-center gap-0.5 text-[9px] text-warning font-medium mt-0.5">
                        <Trophy className="h-2.5 w-2.5" /> Top performer
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-right font-mono text-sm font-medium text-foreground">${outlet.revenue.toFixed(2)}</td>
                  <td className="px-4 py-3 text-right text-sm text-foreground">{outlet.orders}</td>
                  <td className="px-4 py-3 text-right font-mono text-sm text-muted-foreground">${outlet.avgOrderValue.toFixed(2)}</td>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2">
                      <div className="flex-1 h-1.5 bg-muted/30 rounded-full overflow-hidden">
                        <div className="h-full bg-primary/50 rounded-full transition-all" style={{ width: `${share}%` }} />
                      </div>
                      <span className="text-[10px] font-mono text-muted-foreground w-8 text-right">{share.toFixed(0)}%</span>
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════════════════════ */
/* Inventory Health — Real Data                                              */
/* ═══════════════════════════════════════════════════════════════════════════ */

function InventoryHealth() {
  const { scope } = useScopeIds();
  const { lowStockItems, loading } = useReportData(scope);

  const oosCount = lowStockItems.filter(i => i.quantity === 0).length;
  const lowCount = lowStockItems.filter(i => i.quantity > 0).length;

  // Group by outlet
  const byOutlet = lowStockItems.reduce<Record<string, LowStockAlert[]>>((acc, item) => {
    (acc[item.outletName] = acc[item.outletName] || []).push(item);
    return acc;
  }, {});

  if (loading) {
    return <div className="flex items-center justify-center py-20"><Loader2 className="h-5 w-5 animate-spin text-muted-foreground" /></div>;
  }

  return (
    <div className="p-6 space-y-5 animate-fade-in">
      {/* Header */}
      <div>
        <div className="flex items-center gap-1.5 text-[11px] text-muted-foreground mb-1">
          <Layers className="h-3 w-3" />
          <span>Reports</span>
          <ChevronRight className="h-3 w-3" />
          <span className="text-foreground font-medium">Inventory</span>
        </div>
        <h2 className="text-lg font-semibold text-foreground">Inventory Health</h2>
        <p className="text-xs text-muted-foreground mt-0.5">Stock levels and alerts from live database</p>
      </div>

      {/* KPIs */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        {[
          { label: 'Total Alerts', value: String(lowStockItems.length), icon: AlertTriangle, color: lowStockItems.length > 0 ? 'text-warning' : 'text-foreground' },
          { label: 'Low Stock', value: String(lowCount), icon: ArrowDownRight, color: lowCount > 0 ? 'text-warning' : 'text-foreground' },
          { label: 'Out of Stock', value: String(oosCount), icon: XCircle, color: oosCount > 0 ? 'text-destructive' : 'text-foreground' },
          { label: 'Outlets Affected', value: String(Object.keys(byOutlet).length), icon: Store, color: 'text-foreground' },
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

      {/* Alerts by Outlet */}
      <div className="grid grid-cols-1 lg:grid-cols-5 gap-5">
        <div className="lg:col-span-3 surface-elevated overflow-hidden">
          <div className="px-4 py-3 border-b border-border">
            <span className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wide">Low Stock / Out of Stock Items</span>
          </div>
          <table className="w-full">
            <thead>
              <tr className="border-b bg-muted/30">
                <th className="text-[11px] font-medium text-muted-foreground px-4 py-2.5 text-left">Item</th>
                <th className="text-[11px] font-medium text-muted-foreground px-4 py-2.5 text-left">Outlet</th>
                <th className="text-[11px] font-medium text-muted-foreground px-4 py-2.5 text-right">On Hand</th>
                <th className="text-[11px] font-medium text-muted-foreground px-4 py-2.5 text-right">Reorder Level</th>
                <th className="text-[11px] font-medium text-muted-foreground px-4 py-2.5 text-left">Status</th>
              </tr>
            </thead>
            <tbody>
              {lowStockItems.length === 0 ? (
                <tr><td colSpan={5} className="px-4 py-8 text-center text-sm text-muted-foreground">
                  <CheckCircle2 className="h-5 w-5 text-success mx-auto mb-2" />
                  All items are above reorder level
                </td></tr>
              ) : lowStockItems.map((item, i) => (
                <tr key={i} className={cn('border-b last:border-0 hover:bg-muted/20 transition-colors', item.quantity === 0 && 'bg-destructive/[0.02]')}>
                  <td className="px-4 py-2.5">
                    <p className="text-sm font-medium text-foreground">{item.itemName}</p>
                    <p className="text-[10px] text-muted-foreground">{item.category}</p>
                  </td>
                  <td className="px-4 py-2.5 text-xs text-muted-foreground">{item.outletName}</td>
                  <td className={cn('px-4 py-2.5 text-sm text-right font-medium', item.quantity === 0 ? 'text-destructive' : 'text-warning')}>
                    {item.quantity}
                  </td>
                  <td className="px-4 py-2.5 text-sm text-right text-muted-foreground">{item.reorderLevel}</td>
                  <td className="px-4 py-2.5">
                    <span className={cn(
                      'text-[10px] font-medium px-2 py-0.5 rounded-full',
                      item.quantity === 0 ? 'bg-destructive/10 text-destructive' : 'bg-warning/10 text-warning'
                    )}>
                      {item.quantity === 0 ? 'Out of Stock' : 'Low'}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* By Outlet summary */}
        <div className="lg:col-span-2 surface-elevated overflow-hidden">
          <div className="px-4 py-3 border-b border-border">
            <span className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wide">Alerts by Outlet</span>
          </div>
          {Object.keys(byOutlet).length === 0 ? (
            <div className="px-4 py-8 text-center text-sm text-muted-foreground">No alerts</div>
          ) : (
            <div className="divide-y divide-border">
              {Object.entries(byOutlet).map(([outlet, items]) => {
                const oos = items.filter(i => i.quantity === 0).length;
                return (
                  <div key={outlet} className="px-4 py-3 hover:bg-muted/10 transition-colors">
                    <div className="flex items-center justify-between mb-1.5">
                      <p className="text-sm font-medium text-foreground">{outlet}</p>
                      <span className="text-[10px] font-medium text-muted-foreground">{items.length} items</span>
                    </div>
                    <div className="flex items-center gap-3 text-[10px]">
                      {oos > 0 && (
                        <span className="text-destructive flex items-center gap-0.5">
                          <XCircle className="h-2.5 w-2.5" /> {oos} out of stock
                        </span>
                      )}
                      <span className="text-warning flex items-center gap-0.5">
                        <ArrowDownRight className="h-2.5 w-2.5" /> {items.length - oos} low
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
