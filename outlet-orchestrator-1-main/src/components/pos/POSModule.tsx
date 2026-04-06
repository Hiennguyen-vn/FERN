import { useState, useCallback } from 'react';
import { POSSessionList } from '@/components/pos/POSSessionList';
import { OpenPOSSession } from '@/components/pos/OpenPOSSession';
import { POSSessionDetail } from '@/components/pos/POSSessionDetail';
import { OrderEntry } from '@/components/pos/OrderEntry';
import { SaleOrderDetail } from '@/components/pos/SaleOrderDetail';
import { PaymentCapture } from '@/components/pos/PaymentCapture';
import { CancelOrder } from '@/components/pos/CancelOrder';
import { CloseSession } from '@/components/pos/CloseSession';
import { ReconcileSession } from '@/components/pos/ReconcileSession';
import { CustomerPanel } from '@/components/pos/CustomerPanel';
import { OutletStatsPanel } from '@/components/pos/OutletStatsPanel';
import { TableManagement } from '@/components/pos/TableManagement';
import type { POSSession, SaleOrder, OrderLineItem, PaymentMethod } from '@/types/pos';
import { mockOrders as initialMockOrders } from '@/data/mock-pos';
import { usePOSSessions, type DBPosSession } from '@/hooks/use-pos-sessions';
import { Loader2 } from 'lucide-react';

type POSView =
  | { screen: 'list' }
  | { screen: 'open-session' }
  | { screen: 'session-detail'; sessionId: string }
  | { screen: 'edit-session'; sessionId: string }
  | { screen: 'order-entry'; sessionId: string }
  | { screen: 'order-detail'; orderId: string }
  | { screen: 'payment'; sessionId: string; items: OrderLineItem[]; promo: string | null; total: number; subtotal: number; taxAmount: number; promoDiscount: number }
  | { screen: 'cancel-order'; orderId: string }
  | { screen: 'close-session'; sessionId: string }
  | { screen: 'reconcile'; sessionId: string }
  | { screen: 'customers' }
  | { screen: 'outlet-stats' }
  | { screen: 'tables' };

interface Props {
  outletName: string;
  operatorName: string;
  outletId?: string;
}

function dbToUiSession(s: DBPosSession): POSSession {
  return {
    id: s.id,
    code: `POS-${s.opened_at.slice(0, 10).replace(/-/g, '')}-${s.id.slice(0, 3).toUpperCase()}`,
    outletId: s.outlet_id,
    outletName: s.outlet_name || 'Unknown',
    businessDate: s.opened_at.slice(0, 10),
    openedBy: 'Operator',
    openedAt: s.opened_at,
    status: s.status as POSSession['status'],
    closedAt: s.closed_at || undefined,
    openingNote: s.notes || undefined,
    orderCount: 0,
    totalRevenue: 0,
    paymentSummary: [],
  };
}

export function POSModule({ outletName, operatorName, outletId }: Props) {
  const [view, setView] = useState<POSView>({ screen: 'list' });
  const [orders, setOrders] = useState<SaleOrder[]>(initialMockOrders);
  const { sessions: dbSessions, loading, createSession, updateSession, closeSession: dbClose, reconcileSession: dbReconcile, deleteSession, fetchSessions } = usePOSSessions();

  const sessions: POSSession[] = dbSessions.map(dbToUiSession);

  const goList = useCallback(() => setView({ screen: 'list' }), []);
  const getSession = (id: string) => sessions.find(s => s.id === id);
  const getDbSession = (id: string) => dbSessions.find(s => s.id === id);
  const hasOpenSession = sessions.some(s => s.status === 'open');

  const handleCreateSession = useCallback(async (note?: string) => {
    const targetOutlet = outletId || dbSessions[0]?.outlet_id;
    if (!targetOutlet) {
      // fallback - try to find first outlet
      const { toast } = await import('sonner');
      toast.error('No outlet available. Please configure outlets in Settings first.');
      return;
    }
    const result = await createSession(targetOutlet, 200, note);
    if (result) {
      setView({ screen: 'session-detail', sessionId: result.id });
    }
  }, [outletId, dbSessions, createSession]);

  const handleCloseSession = useCallback(async (sessionId: string) => {
    await dbClose(sessionId, 0);
    goList();
  }, [dbClose, goList]);

  const handleReconcileSession = useCallback(async (sessionId: string) => {
    await dbReconcile(sessionId);
    goList();
  }, [dbReconcile, goList]);

  const handleDeleteSession = useCallback(async (sessionId: string) => {
    await deleteSession(sessionId);
    goList();
  }, [deleteSession, goList]);

  const handleEditSession = useCallback(async (sessionId: string, updates: { notes?: string; opening_float?: number }) => {
    await updateSession(sessionId, { notes: updates.notes, opening_float: updates.opening_float });
    setView({ screen: 'session-detail', sessionId });
  }, [updateSession]);

  const handlePaymentComplete = useCallback((sessionId: string, items: OrderLineItem[], promo: string | null, total: number, subtotal: number, taxAmount: number, promoDiscount: number, paymentMethod: PaymentMethod) => {
    const session = getSession(sessionId);
    if (!session) return;
    const orderNum = `SO-${(5000 + orders.length).toString()}`;
    const newOrder: SaleOrder = {
      id: `ord-${Date.now()}`,
      orderNumber: orderNum,
      sessionId,
      sessionCode: session.code,
      outletName,
      createdBy: operatorName,
      createdAt: new Date().toISOString(),
      status: 'completed',
      paymentStatus: 'paid',
      lineItems: items,
      subtotal,
      taxAmount,
      total,
      promotionCode: promo || undefined,
      promotionDiscount: promoDiscount || undefined,
      payments: [{ id: `pay-${Date.now()}`, method: paymentMethod, amount: total, capturedAt: new Date().toISOString() }],
    };
    setOrders(prev => [newOrder, ...prev]);
    setView({ screen: 'session-detail', sessionId });
  }, [orders, outletName, operatorName, sessions]);

  const handleCancelOrder = useCallback((orderId: string, reason: string) => {
    setOrders(prev => prev.map(o => o.id === orderId ? { ...o, status: 'cancelled' as const, cancelReason: reason } : o));
    const order = orders.find(o => o.id === orderId);
    if (order) setView({ screen: 'session-detail', sessionId: order.sessionId });
    else goList();
  }, [orders, goList]);

  if (loading) {
    return <div className="flex items-center justify-center h-full py-20"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /></div>;
  }

  if (view.screen === 'customers') return <CustomerPanel onBack={goList} gatewayAvailable={true} />;
  if (view.screen === 'outlet-stats') return <OutletStatsPanel onBack={goList} gatewayAvailable={true} />;
  if (view.screen === 'tables') return <TableManagement onBack={goList} gatewayAvailable={true} permissionsBootstrapped={true} />;

  if (view.screen === 'list') {
    return (
      <POSSessionList
        sessions={sessions}
        onOpenSession={() => setView({ screen: 'open-session' })}
        onViewSession={(session) => setView({ screen: 'session-detail', sessionId: session.id })}
        onCloseSession={(session) => setView({ screen: 'close-session', sessionId: session.id })}
        onReconcile={(session) => setView({ screen: 'reconcile', sessionId: session.id })}
        onEditSession={(session) => setView({ screen: 'edit-session', sessionId: session.id })}
        onDeleteSession={(session) => handleDeleteSession(session.id)}
        onCustomers={() => setView({ screen: 'customers' })}
        onOutletStats={() => setView({ screen: 'outlet-stats' })}
        onTables={() => setView({ screen: 'tables' })}
      />
    );
  }

  if (view.screen === 'open-session') {
    return (
      <OpenPOSSession
        outletName={outletName}
        operatorName={operatorName}
        hasOpenSession={hasOpenSession}
        onBack={goList}
        onOpen={(note) => handleCreateSession(note)}
      />
    );
  }

  if (view.screen === 'edit-session') {
    const dbS = getDbSession(view.sessionId);
    if (!dbS) return <div className="p-6 text-sm text-muted-foreground">Session not found</div>;
    return (
      <EditPOSSession
        session={dbS}
        onBack={() => setView({ screen: 'session-detail', sessionId: view.sessionId })}
        onSave={(updates) => handleEditSession(view.sessionId, updates)}
      />
    );
  }

  if (view.screen === 'session-detail') {
    const session = getSession(view.sessionId);
    if (!session) return <div className="p-6 text-sm text-muted-foreground">Session not found</div>;
    const sessionOrders = orders.filter(o => o.sessionId === session.id);
    return (
      <POSSessionDetail
        session={session}
        orders={sessionOrders}
        onBack={goList}
        onClose={() => setView({ screen: 'close-session', sessionId: session.id })}
        onReconcile={() => setView({ screen: 'reconcile', sessionId: session.id })}
        onNewOrder={() => setView({ screen: 'order-entry', sessionId: session.id })}
        onViewOrder={(orderId) => setView({ screen: 'order-detail', orderId })}
      />
    );
  }

  if (view.screen === 'order-entry') {
    const session = getSession(view.sessionId);
    if (!session) return <div className="p-6 text-sm text-muted-foreground">Session not found</div>;
    return (
      <OrderEntry
        sessionCode={session.code}
        outletName={outletName}
        cashierName={operatorName}
        onBack={() => setView({ screen: 'session-detail', sessionId: view.sessionId })}
        onCheckout={(items, promo) => {
          const subtotal = items.reduce((s, i) => s + i.lineTotal, 0);
          const promoDiscount = promo === 'LUNCH20' ? +(subtotal * 0.2).toFixed(2) : 0;
          const adjustedSubtotal = subtotal - promoDiscount;
          const taxAmount = +(adjustedSubtotal * 0.08).toFixed(2);
          const total = +(adjustedSubtotal + taxAmount).toFixed(2);
          setView({ screen: 'payment', sessionId: view.sessionId, items, promo, total, subtotal, taxAmount, promoDiscount });
        }}
      />
    );
  }

  if (view.screen === 'order-detail') {
    const order = orders.find(o => o.id === view.orderId);
    if (!order) return <div className="p-6 text-sm text-muted-foreground">Order not found</div>;
    return (
      <SaleOrderDetail
        order={order}
        onBack={() => setView({ screen: 'session-detail', sessionId: order.sessionId })}
        onPay={() => setView({ screen: 'payment', sessionId: order.sessionId, items: order.lineItems, promo: order.promotionCode || null, total: order.total, subtotal: order.subtotal, taxAmount: order.taxAmount, promoDiscount: order.promotionDiscount || 0 })}
        onCancel={() => setView({ screen: 'cancel-order', orderId: order.id })}
      />
    );
  }

  if (view.screen === 'payment') {
    return (
      <PaymentCapture
        orderTotal={view.total}
        lineItems={view.items}
        promoCode={view.promo}
        promoDiscount={view.promoDiscount}
        subtotal={view.subtotal}
        taxAmount={view.taxAmount}
        onBack={() => setView({ screen: 'session-detail', sessionId: view.sessionId })}
        onComplete={(paymentMethod) => handlePaymentComplete(view.sessionId, view.items, view.promo, view.total, view.subtotal, view.taxAmount, view.promoDiscount, paymentMethod)}
      />
    );
  }

  if (view.screen === 'cancel-order') {
    const order = orders.find(o => o.id === view.orderId);
    if (!order) return <div className="p-6 text-sm text-muted-foreground">Order not found</div>;
    return (
      <CancelOrder
        order={order}
        onBack={() => setView({ screen: 'order-detail', orderId: order.id })}
        onConfirm={(reason) => handleCancelOrder(order.id, reason)}
      />
    );
  }

  if (view.screen === 'close-session') {
    const session = getSession(view.sessionId);
    if (!session) return <div className="p-6 text-sm text-muted-foreground">Session not found</div>;
    return (
      <CloseSession
        session={session}
        onBack={() => setView({ screen: 'session-detail', sessionId: session.id })}
        onConfirm={() => handleCloseSession(session.id)}
      />
    );
  }

  if (view.screen === 'reconcile') {
    const session = getSession(view.sessionId);
    if (!session) return <div className="p-6 text-sm text-muted-foreground">Session not found</div>;
    return (
      <ReconcileSession
        session={session}
        onBack={() => setView({ screen: 'session-detail', sessionId: session.id })}
        onConfirm={() => handleReconcileSession(session.id)}
      />
    );
  }

  return null;
}

/* ── Edit Session Component ── */
import { ArrowLeft, Monitor } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

function EditPOSSession({ session, onBack, onSave }: {
  session: DBPosSession;
  onBack: () => void;
  onSave: (updates: { notes?: string; opening_float?: number }) => void;
}) {
  const [notes, setNotes] = useState(session.notes || '');
  const [openingFloat, setOpeningFloat] = useState(String(session.opening_float));
  const [saving, setSaving] = useState(false);

  const handleSave = async () => {
    setSaving(true);
    await onSave({
      notes: notes || undefined,
      opening_float: parseFloat(openingFloat) || 0,
    });
    setSaving(false);
  };

  return (
    <div className="p-6 animate-fade-in">
      <button onClick={onBack} className="flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground transition-colors mb-4">
        <ArrowLeft className="h-3 w-3" /> Back to session
      </button>
      <div className="max-w-lg mx-auto">
        <div className="surface-elevated p-6 space-y-6">
          <div className="text-center">
            <div className="mx-auto h-12 w-12 rounded-xl bg-primary/10 flex items-center justify-center mb-3">
              <Monitor className="h-6 w-6 text-primary" />
            </div>
            <h2 className="text-lg font-semibold text-foreground">Edit POS Session</h2>
            <p className="text-sm text-muted-foreground mt-1">Update session details</p>
          </div>
          <div className="space-y-4">
            <div>
              <Label className="text-xs">Opening Float ($)</Label>
              <Input
                type="number"
                value={openingFloat}
                onChange={e => setOpeningFloat(e.target.value)}
                className="mt-1 h-9"
                disabled={session.status !== 'open'}
              />
            </div>
            <div>
              <Label className="text-xs">Notes</Label>
              <Input
                value={notes}
                onChange={e => setNotes(e.target.value)}
                className="mt-1 h-9"
                placeholder="Session notes..."
              />
            </div>
          </div>
          <Button className="w-full h-10" onClick={handleSave} disabled={saving}>
            {saving ? <><Loader2 className="h-4 w-4 animate-spin" /> Saving...</> : 'Save Changes'}
          </Button>
        </div>
      </div>
    </div>
  );
}
