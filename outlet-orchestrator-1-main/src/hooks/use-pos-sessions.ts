import { useState, useEffect, useCallback } from 'react';
import { supabase } from '@/integrations/supabase/client';
import { toast } from 'sonner';

export interface DBPosSession {
  id: string;
  outlet_id: string;
  operator_id: string;
  status: string;
  opening_float: number;
  closing_cash: number | null;
  notes: string | null;
  opened_at: string;
  closed_at: string | null;
  created_at: string;
  updated_at: string;
  // joined
  outlet_name?: string;
}

export function usePOSSessions() {
  const [sessions, setSessions] = useState<DBPosSession[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchSessions = useCallback(async () => {
    setLoading(true);
    const { data, error } = await supabase
      .from('pos_sessions')
      .select('*, outlets(name)')
      .order('opened_at', { ascending: false });

    if (error) {
      console.error('Error fetching POS sessions:', error);
      setSessions([]);
    } else {
      setSessions((data || []).map((s: any) => ({
        ...s,
        outlet_name: s.outlets?.name || 'Unknown',
      })));
    }
    setLoading(false);
  }, []);

  useEffect(() => { fetchSessions(); }, [fetchSessions]);

  const createSession = async (outletId: string, openingFloat: number, notes?: string) => {
    const { data: { user } } = await supabase.auth.getUser();
    if (!user) { toast.error('Please sign in first'); return null; }

    const { data, error } = await supabase.from('pos_sessions').insert({
      outlet_id: outletId,
      operator_id: user.id,
      opening_float: openingFloat,
      notes: notes || null,
    }).select('*, outlets(name)').single();

    if (error) { toast.error(error.message); return null; }
    toast.success('POS Session opened');
    await fetchSessions();
    return data;
  };

  const updateSession = async (id: string, updates: {
    status?: string;
    closing_cash?: number | null;
    closed_at?: string | null;
    notes?: string | null;
    opening_float?: number;
  }) => {
    const { error } = await supabase.from('pos_sessions').update(updates).eq('id', id);
    if (error) { toast.error(error.message); return false; }
    toast.success('Session updated');
    await fetchSessions();
    return true;
  };

  const closeSession = async (id: string, closingCash: number) => {
    return updateSession(id, {
      status: 'closed',
      closing_cash: closingCash,
      closed_at: new Date().toISOString(),
    });
  };

  const reconcileSession = async (id: string) => {
    return updateSession(id, { status: 'reconciled' });
  };

  const deleteSession = async (id: string) => {
    const { error } = await supabase.from('pos_sessions').delete().eq('id', id);
    if (error) { toast.error(error.message); return false; }
    toast.success('Session deleted');
    await fetchSessions();
    return true;
  };

  return {
    sessions, loading, fetchSessions,
    createSession, updateSession, closeSession, reconcileSession, deleteSession,
  };
}
