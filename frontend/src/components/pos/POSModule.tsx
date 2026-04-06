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
import { toast } from 'sonner';

type POSView =
  | { screen: 'list' }
  | { screen: 'open-session' }
  | { screen: 'session-detail'; sessionId: number }
  | { screen: 'order-entry'; sessionId: number }
  | { screen: 'order-detail'; orderId: string }
  | {
      screen: 'payment';
      sessionId: number;
      items: OrderLineItem[];
      promo: string | null;
      total: number;
      subtotal: number;
      taxAmount: number;
      promoDiscount: number;
    }
  | { screen: 'cancel-order'; orderId: string }
  | { screen: 'close-session'; sessionId: number }
  | { screen: 'reconcile'; sessionId: number }
  | { screen: 'customers' }
  | { screen: 'outlet-stats' }
  | { screen: 'tables' };

interface Props {
  outletName: string;
  operatorName: string;
  outletId?: number;
}

function dbToUiSession(s: DBPosSession): POSSession {
  return {
    id: String(s.id),
    code: `POS-${s.business_date.replace(/-/g, '')}-${String(s.id).padStart(4, '0')}`,
    outletId: String(s.outlet_id),
    outletName: s.outlet_name || 'Unknown',
    businessDate: s.business_date,
    openedBy: 'Operator',
    openedAt: s.opened_at,
    status: s.status as POSSession['status'],
    closedAt: s.closed_at || undefined,
    reconciledAt: s.reconciled_at || undefined,
    openingNote: s.notes || undefined,
    orderCount: 0,
    totalRevenue: 0,
    paymentSummary: [],
  };
}

export function POSModule({ outletName, operatorName, outletId }: Props) {
  const [view, setView] = useState<POSView>({ screen: 'list' });
  const [orders, setOrders] = useState<SaleOrder[]>(initialMockOrders);
  const {
    sessions: dbSessions,
    loading,
    createSession,
    closeSession: closeSessionApi,
    reconcileSession: reconcileSessionApi,
  } = usePOSSessions(outletId);

  const sessions: POSSession[] = dbSessions.map(dbToUiSession);

  const goList = useCallback(() => setView({ screen: 'list' }), []);
  const getSession = (id: number) => sessions.find((s) => s.id === String(id));
  const hasOpenSession = sessions.some((s) => s.status === 'open');

  const handleCreateSession = useCallback(
    async (note?: string) => {
      if (!outletId) {
        toast.error('No outlet selected. Please pick an outlet scope before opening a session.');
        return;
      }
      const result = await createSession(note);
      if (result) {
        setView({ screen: 'session-detail', sessionId: result.id });
      }
    },
    [outletId, createSession]
  );

  const handleCloseSession = useCallback(
    async (sessionId: number) => {
      await closeSessionApi(sessionId);
      goList();
    },
    [closeSessionApi, goList]
  );

  const handleReconcileSession = useCallback(
    async (sessionId: number) => {
      const session = dbSessions.find((entry) => entry.id === sessionId);
      await reconcileSessionApi(sessionId, session?.expected_cash_amount);
      goList();
    },
    [reconcileSessionApi, goList, dbSessions]
  );

  const handlePaymentComplete = useCallback(
    (
      sessionId: number,
      items: OrderLineItem[],
      promo: string | null,
      total: number,
      subtotal: number,
      taxAmount: number,
      promoDiscount: number,
      paymentMethod: PaymentMethod
    ) => {
      const session = sessions.find((entry) => entry.id === String(sessionId));
      if (!session) return;
      const orderNum = `SO-${(5000 + orders.length).toString()}`;
      const newOrder: SaleOrder = {
        id: `ord-${Date.now()}`,
        orderNumber: orderNum,
        sessionId: String(sessionId),
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
      setOrders((prev) => [newOrder, ...prev]);
      setView({ screen: 'session-detail', sessionId });
    },
    [orders, outletName, operatorName, sessions]
  );

  const handleCancelOrder = useCallback(
    (orderId: string, reason: string) => {
      setOrders((prev) => prev.map((o) => (o.id === orderId ? { ...o, status: 'cancelled' as const, cancelReason: reason } : o)));
      const order = orders.find((o) => o.id === orderId);
      if (order) setView({ screen: 'session-detail', sessionId: Number(order.sessionId) });
      else goList();
    },
    [orders, goList]
  );

  if (loading) {
    return (
      <div className="flex items-center justify-center h-full py-20">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (view.screen === 'customers') return <CustomerPanel onBack={goList} gatewayAvailable={true} />;
  if (view.screen === 'outlet-stats') return <OutletStatsPanel onBack={goList} gatewayAvailable={true} />;
  if (view.screen === 'tables') return <TableManagement onBack={goList} gatewayAvailable={true} permissionsBootstrapped={true} />;

  if (view.screen === 'list') {
    return (
      <POSSessionList
        sessions={sessions}
        onOpenSession={() => setView({ screen: 'open-session' })}
        onViewSession={(session) => setView({ screen: 'session-detail', sessionId: Number(session.id) })}
        onCloseSession={(session) => setView({ screen: 'close-session', sessionId: Number(session.id) })}
        onReconcile={(session) => setView({ screen: 'reconcile', sessionId: Number(session.id) })}
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

  if (view.screen === 'session-detail') {
    const session = getSession(view.sessionId);
    if (!session) return <div className="p-6 text-sm text-muted-foreground">Session not found</div>;
    const sessionOrders = orders.filter((o) => o.sessionId === session.id);
    return (
      <POSSessionDetail
        session={session}
        orders={sessionOrders}
        onBack={goList}
        onClose={() => setView({ screen: 'close-session', sessionId: Number(session.id) })}
        onReconcile={() => setView({ screen: 'reconcile', sessionId: Number(session.id) })}
        onNewOrder={() => setView({ screen: 'order-entry', sessionId: Number(session.id) })}
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
    const order = orders.find((o) => o.id === view.orderId);
    if (!order) return <div className="p-6 text-sm text-muted-foreground">Order not found</div>;
    return (
      <SaleOrderDetail
        order={order}
        onBack={() => setView({ screen: 'session-detail', sessionId: Number(order.sessionId) })}
        onPay={() =>
          setView({
            screen: 'payment',
            sessionId: Number(order.sessionId),
            items: order.lineItems,
            promo: order.promotionCode || null,
            total: order.total,
            subtotal: order.subtotal,
            taxAmount: order.taxAmount,
            promoDiscount: order.promotionDiscount || 0,
          })
        }
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
        onComplete={(paymentMethod) =>
          handlePaymentComplete(view.sessionId, view.items, view.promo, view.total, view.subtotal, view.taxAmount, view.promoDiscount, paymentMethod)
        }
      />
    );
  }

  if (view.screen === 'cancel-order') {
    const order = orders.find((o) => o.id === view.orderId);
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
        onBack={() => setView({ screen: 'session-detail', sessionId: Number(session.id) })}
        onConfirm={() => handleCloseSession(Number(session.id))}
      />
    );
  }

  if (view.screen === 'reconcile') {
    const session = getSession(view.sessionId);
    if (!session) return <div className="p-6 text-sm text-muted-foreground">Session not found</div>;
    return (
      <ReconcileSession
        session={session}
        onBack={() => setView({ screen: 'session-detail', sessionId: Number(session.id) })}
        onConfirm={() => handleReconcileSession(Number(session.id))}
      />
    );
  }

  return null;
}
