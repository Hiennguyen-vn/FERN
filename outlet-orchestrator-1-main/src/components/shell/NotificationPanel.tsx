import { useState } from 'react';
import {
  Bell, X, Check, CheckCheck, Info, AlertTriangle, Zap,
  Package, ShoppingCart, Users, BarChart3, Clock,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';

interface Notification {
  id: string;
  title: string;
  message: string;
  module: string;
  type: 'info' | 'warning' | 'action' | 'success';
  isRead: boolean;
  createdAt: string;
}

const MODULE_ICONS: Record<string, React.ElementType> = {
  pos: ShoppingCart, inventory: Package, procurement: ShoppingCart,
  hr: Users, reports: BarChart3, system: Zap, finance: Clock,
};

const TYPE_STYLES: Record<string, { icon: React.ElementType; cls: string }> = {
  info: { icon: Info, cls: 'text-primary bg-primary/10' },
  warning: { icon: AlertTriangle, cls: 'text-warning bg-warning/10' },
  action: { icon: Zap, cls: 'text-accent-foreground bg-accent' },
  success: { icon: Check, cls: 'text-success bg-success/10' },
};

const MOCK_NOTIFICATIONS: Notification[] = [
  { id: '1', title: 'PO Approval Required', message: 'PO-2026-0401 from Metro Foods needs your approval. Total: $4,280.00', module: 'procurement', type: 'action', isRead: false, createdAt: '2026-04-05T14:30:00Z' },
  { id: '2', title: 'Low Stock Alert', message: 'Arabica Coffee Beans at Downtown Flagship is below reorder level (12 units remaining)', module: 'inventory', type: 'warning', isRead: false, createdAt: '2026-04-05T13:15:00Z' },
  { id: '3', title: 'Payroll Run Submitted', message: 'Central Region payroll for March 2026 has been submitted for approval', module: 'finance', type: 'info', isRead: false, createdAt: '2026-04-05T11:00:00Z' },
  { id: '4', title: 'Attendance Exception', message: 'Aisha Patel clocked in 22 minutes late today — review required', module: 'hr', type: 'warning', isRead: true, createdAt: '2026-04-05T09:22:00Z' },
  { id: '5', title: 'Daily Sales Report Ready', message: 'Your daily sales summary for April 4 is available', module: 'reports', type: 'success', isRead: true, createdAt: '2026-04-04T23:00:00Z' },
  { id: '6', title: 'Session Reconciled', message: 'POS Session #SES-0042 reconciled with $0.50 variance', module: 'pos', type: 'info', isRead: true, createdAt: '2026-04-04T21:30:00Z' },
  { id: '7', title: 'System Update', message: 'Platform maintenance scheduled for April 7, 2:00 AM — 4:00 AM UTC', module: 'system', type: 'info', isRead: true, createdAt: '2026-04-04T10:00:00Z' },
];

function timeAgo(dateStr: string) {
  const diff = Date.now() - new Date(dateStr).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  return `${Math.floor(hrs / 24)}d ago`;
}

type FilterType = 'all' | 'unread' | 'action' | 'warning';

interface NotificationPanelProps {
  open: boolean;
  onClose: () => void;
}

export function NotificationPanel({ open, onClose }: NotificationPanelProps) {
  const [notifications, setNotifications] = useState(MOCK_NOTIFICATIONS);
  const [filter, setFilter] = useState<FilterType>('all');

  if (!open) return null;

  const unreadCount = notifications.filter(n => !n.isRead).length;

  const filtered = notifications.filter(n => {
    if (filter === 'unread') return !n.isRead;
    if (filter === 'action') return n.type === 'action';
    if (filter === 'warning') return n.type === 'warning';
    return true;
  });

  const markAllRead = () => setNotifications(prev => prev.map(n => ({ ...n, isRead: true })));
  const toggleRead = (id: string) => setNotifications(prev =>
    prev.map(n => n.id === id ? { ...n, isRead: !n.isRead } : n)
  );

  return (
    <>
      <div className="fixed inset-0 bg-foreground/20 z-40" onClick={onClose} />
      <div className="fixed right-4 top-16 w-[380px] max-h-[70vh] bg-card rounded-xl border shadow-surface-xl z-50 animate-fade-in flex flex-col">
        {/* Header */}
        <div className="px-5 pt-5 pb-3 border-b flex-shrink-0">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <h2 className="text-sm font-semibold text-foreground">Notifications</h2>
              {unreadCount > 0 && (
                <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-destructive text-destructive-foreground font-medium">
                  {unreadCount}
                </span>
              )}
            </div>
            <div className="flex items-center gap-1">
              {unreadCount > 0 && (
                <Button variant="ghost" size="sm" className="h-7 text-[10px] px-2 gap-1" onClick={markAllRead}>
                  <CheckCheck className="h-3 w-3" /> Mark all read
                </Button>
              )}
              <button onClick={onClose} className="text-muted-foreground hover:text-foreground transition-colors p-1">
                <X className="h-4 w-4" />
              </button>
            </div>
          </div>

          {/* Filters */}
          <div className="flex items-center gap-1 mt-3">
            {([
              { key: 'all' as FilterType, label: 'All' },
              { key: 'unread' as FilterType, label: `Unread (${unreadCount})` },
              { key: 'action' as FilterType, label: 'Actions' },
              { key: 'warning' as FilterType, label: 'Warnings' },
            ]).map(f => (
              <button
                key={f.key}
                onClick={() => setFilter(f.key)}
                className={cn(
                  'text-[10px] px-2 py-1 rounded-md transition-colors',
                  filter === f.key
                    ? 'bg-primary text-primary-foreground'
                    : 'bg-muted text-muted-foreground hover:text-foreground'
                )}
              >
                {f.label}
              </button>
            ))}
          </div>
        </div>

        {/* Notification list */}
        <div className="flex-1 overflow-y-auto">
          {filtered.length === 0 ? (
            <div className="p-8 text-center">
              <Bell className="h-8 w-8 text-muted-foreground/30 mx-auto mb-2" />
              <p className="text-xs text-muted-foreground">No notifications</p>
            </div>
          ) : (
            <div className="divide-y">
              {filtered.map(n => {
                const typeStyle = TYPE_STYLES[n.type] || TYPE_STYLES.info;
                const TypeIcon = typeStyle.icon;
                return (
                  <div
                    key={n.id}
                    className={cn(
                      'px-5 py-3 hover:bg-muted/30 transition-colors cursor-pointer relative',
                      !n.isRead && 'bg-primary/[0.02]'
                    )}
                    onClick={() => toggleRead(n.id)}
                  >
                    {!n.isRead && (
                      <span className="absolute left-2 top-1/2 -translate-y-1/2 h-1.5 w-1.5 rounded-full bg-primary" />
                    )}
                    <div className="flex gap-3">
                      <div className={cn('h-7 w-7 rounded-md flex items-center justify-center flex-shrink-0 mt-0.5', typeStyle.cls)}>
                        <TypeIcon className="h-3.5 w-3.5" />
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center justify-between gap-2">
                          <p className={cn('text-xs font-medium truncate', n.isRead ? 'text-muted-foreground' : 'text-foreground')}>
                            {n.title}
                          </p>
                          <span className="text-[10px] text-muted-foreground flex-shrink-0">
                            {timeAgo(n.createdAt)}
                          </span>
                        </div>
                        <p className="text-[11px] text-muted-foreground mt-0.5 line-clamp-2">{n.message}</p>
                        <span className="text-[9px] uppercase tracking-wider text-muted-foreground/70 mt-1 inline-block">
                          {n.module}
                        </span>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </>
  );
}
