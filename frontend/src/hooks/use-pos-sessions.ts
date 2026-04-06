import { useState, useEffect, useCallback } from 'react';
import { getOutlet, getRegion } from '@/lib/api/org';
import { listSessions, openSession, closeSession as closeSessionApi, reconcileSession as reconcileSessionApi } from '@/lib/api/pos';
import type { PosSessionResponse } from '@/lib/api/types';
import { toast } from 'sonner';

export interface DBPosSession {
  id: number;
  outlet_id: number;
  region_id: number;
  status: string;
  currency_code: string;
  business_date: string;
  terminal_id?: string;
  cashier_user_id?: number;
  manager_user_id?: number;
  closing_cash: number | null;
  notes: string | null;
  opened_at: string;
  closed_at: string | null;
  reconciled_at: string | null;
  expected_cash_amount: number | null;
  counted_cash_amount: number | null;
  discrepancy_amount: number | null;
  created_at: string;
  updated_at: string;
  outlet_name?: string;
}

function toInputDate(value: Date, timeZone: string): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(value);
}

function mapSession(s: PosSessionResponse, outletName: string): DBPosSession {
  return {
    id: s.id,
    outlet_id: s.outletId,
    region_id: s.regionId,
    status: String(s.status || '').toLowerCase(),
    currency_code: s.currencyCode,
    business_date: s.businessDate,
    terminal_id: s.terminalId,
    cashier_user_id: s.cashierUserId,
    manager_user_id: s.managerUserId,
    closing_cash: s.countedCashAmount !== undefined ? Number(s.countedCashAmount) : null,
    notes: s.note || null,
    opened_at: s.openedAt,
    closed_at: s.closedAt || null,
    reconciled_at: s.reconciledAt || null,
    expected_cash_amount: s.expectedCashAmount !== undefined ? Number(s.expectedCashAmount) : null,
    counted_cash_amount: s.countedCashAmount !== undefined ? Number(s.countedCashAmount) : null,
    discrepancy_amount: s.discrepancyAmount !== undefined ? Number(s.discrepancyAmount) : null,
    created_at: s.openedAt,
    updated_at: s.reconciledAt || s.closedAt || s.openedAt,
    outlet_name: outletName,
  };
}

export function usePOSSessions(outletId?: number | null) {
  const [sessions, setSessions] = useState<DBPosSession[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchSessions = useCallback(async () => {
    if (!outletId) {
      setSessions([]);
      setLoading(false);
      return;
    }

    setLoading(true);
    try {
      const [outlet, data] = await Promise.all([
        getOutlet(outletId),
        listSessions(outletId, { limit: 200 }),
      ]);
      setSessions((data || []).map((s) => mapSession(s, outlet.name)));
    } catch (error) {
      console.error('Error fetching POS sessions:', error);
      setSessions([]);
    }
    setLoading(false);
  }, [outletId]);

  useEffect(() => { fetchSessions(); }, [fetchSessions]);

  const createSession = async (notes?: string) => {
    if (!outletId) {
      toast.error('No outlet selected');
      return null;
    }
    try {
      const outlet = await getOutlet(outletId);
      const region = await getRegion(outlet.regionId);
      const payload = {
        regionId: outlet.regionId,
        outletId: outlet.id,
        currencyCode: region.currencyCode,
        businessDate: toInputDate(new Date(), region.timezoneName),
        note: notes || undefined,
      };
      const data = await openSession(payload);
      toast.success('POS Session opened');
      await fetchSessions();
      return mapSession(data, outlet.name);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to open session');
      return null;
    }
  };

  const closeSession = async (id: number) => {
    try {
      await closeSessionApi(id);
      toast.success('Session closed');
      await fetchSessions();
      return true;
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to close session');
      return false;
    }
  };

  const reconcileSession = async (id: number, countedCashAmount?: number | null, note?: string) => {
    try {
      await reconcileSessionApi(id, {
        countedCashAmount: String(countedCashAmount ?? 0),
        note: note || undefined,
      });
      toast.success('Session reconciled');
      await fetchSessions();
      return true;
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to reconcile session');
      return false;
    }
  };

  return {
    sessions, loading, fetchSessions,
    createSession, closeSession, reconcileSession,
  };
}
