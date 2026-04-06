import { useState, useEffect } from 'react';
import { supabase } from '@/integrations/supabase/client';

export interface DashboardKPIs {
  totalRevenue: number;
  totalOrders: number;
  completedOrders: number;
  avgOrderValue: number;
  activeSessions: number;
  lowStockCount: number;
  outOfStockCount: number;
  pendingOrders: number;
}

export interface RecentOrder {
  id: string;
  order_number: string;
  total: number;
  status: string;
  order_type: string | null;
  table_number: string | null;
  created_at: string;
}

export interface LowStockAlert {
  itemName: string;
  category: string | null;
  quantity: number;
  reorderLevel: number | null;
  outletName: string;
  critical: boolean;
}

export function useDashboardData() {
  const [kpis, setKpis] = useState<DashboardKPIs>({
    totalRevenue: 0, totalOrders: 0, completedOrders: 0, avgOrderValue: 0,
    activeSessions: 0, lowStockCount: 0, outOfStockCount: 0, pendingOrders: 0,
  });
  const [recentOrders, setRecentOrders] = useState<RecentOrder[]>([]);
  const [lowStock, setLowStock] = useState<LowStockAlert[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchData = async () => {
    setLoading(true);
    try {
      // Fetch orders
      const { data: orders } = await supabase.from('sale_orders').select('*').order('created_at', { ascending: false }).limit(50);
      const allOrders = orders || [];
      const completed = allOrders.filter(o => o.status === 'completed');
      const totalRev = completed.reduce((s, o) => s + Number(o.total), 0);
      const pending = allOrders.filter(o => o.status === 'preparing' || o.status === 'draft').length;

      // Sessions
      const { data: sessions } = await supabase.from('pos_sessions').select('id, status');
      const activeSess = (sessions || []).filter(s => s.status === 'open').length;

      // Stock balances with items
      const { data: balances } = await supabase.from('stock_balances').select('quantity, item_id, outlet_id');
      const { data: items } = await supabase.from('stock_items').select('id, name, category, reorder_level');
      const { data: outlets } = await supabase.from('outlets').select('id, name');

      const itemMap = new Map((items || []).map(i => [i.id, i]));
      const outletMap = new Map((outlets || []).map(o => [o.id, o]));

      const alerts: LowStockAlert[] = [];
      let lowCount = 0, oosCount = 0;
      (balances || []).forEach(b => {
        const item = itemMap.get(b.item_id);
        const outlet = outletMap.get(b.outlet_id);
        if (!item) return;
        const reorder = item.reorder_level || 0;
        if (b.quantity <= reorder) {
          const critical = b.quantity === 0 || b.quantity <= reorder * 0.3;
          if (b.quantity === 0) oosCount++;
          lowCount++;
          alerts.push({
            itemName: item.name,
            category: item.category,
            quantity: b.quantity,
            reorderLevel: item.reorder_level,
            outletName: outlet?.name || 'Unknown',
            critical,
          });
        }
      });

      setKpis({
        totalRevenue: totalRev,
        totalOrders: allOrders.length,
        completedOrders: completed.length,
        avgOrderValue: completed.length > 0 ? totalRev / completed.length : 0,
        activeSessions: activeSess,
        lowStockCount: lowCount,
        outOfStockCount: oosCount,
        pendingOrders: pending,
      });

      setRecentOrders(allOrders.slice(0, 10).map(o => ({
        id: o.id, order_number: o.order_number, total: Number(o.total),
        status: o.status, order_type: o.order_type, table_number: o.table_number,
        created_at: o.created_at,
      })));

      setLowStock(alerts.sort((a, b) => a.quantity - b.quantity).slice(0, 10));
    } catch (err) {
      console.error('Dashboard fetch error:', err);
    }
    setLoading(false);
  };

  useEffect(() => { fetchData(); }, []);

  return { kpis, recentOrders, lowStock, loading, refresh: fetchData };
}

export function useReportData() {
  const [outletRevenue, setOutletRevenue] = useState<{ outletId: string; outletName: string; revenue: number; orders: number; avgOrderValue: number }[]>([]);
  const [lowStockItems, setLowStockItems] = useState<LowStockAlert[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetch = async () => {
      // Get orders with session → outlet mapping
      const { data: orders } = await supabase.from('sale_orders').select('total, status, session_id');
      const { data: sessions } = await supabase.from('pos_sessions').select('id, outlet_id');
      const { data: outlets } = await supabase.from('outlets').select('id, name');
      const { data: balances } = await supabase.from('stock_balances').select('quantity, item_id, outlet_id');
      const { data: items } = await supabase.from('stock_items').select('id, name, category, reorder_level');

      const sessionOutletMap = new Map((sessions || []).map(s => [s.id, s.outlet_id]));
      const outletMap = new Map((outlets || []).map(o => [o.id, o.name]));
      const itemMap = new Map((items || []).map(i => [i.id, i]));

      // Revenue by outlet
      const revMap: Record<string, { revenue: number; orders: number }> = {};
      (orders || []).filter(o => o.status === 'completed').forEach(o => {
        const outletId = sessionOutletMap.get(o.session_id) || 'unknown';
        if (!revMap[outletId]) revMap[outletId] = { revenue: 0, orders: 0 };
        revMap[outletId].revenue += Number(o.total);
        revMap[outletId].orders += 1;
      });

      const outletRev = Object.entries(revMap)
        .map(([id, d]) => ({
          outletId: id,
          outletName: outletMap.get(id) || 'Unknown',
          revenue: d.revenue,
          orders: d.orders,
          avgOrderValue: d.orders > 0 ? d.revenue / d.orders : 0,
        }))
        .sort((a, b) => b.revenue - a.revenue);

      setOutletRevenue(outletRev);

      // Low stock
      const alerts: LowStockAlert[] = [];
      (balances || []).forEach(b => {
        const item = itemMap.get(b.item_id);
        if (!item) return;
        const reorder = item.reorder_level || 0;
        if (b.quantity <= reorder) {
          alerts.push({
            itemName: item.name,
            category: item.category,
            quantity: b.quantity,
            reorderLevel: item.reorder_level,
            outletName: outletMap.get(b.outlet_id) || 'Unknown',
            critical: b.quantity === 0,
          });
        }
      });
      setLowStockItems(alerts.sort((a, b) => a.quantity - b.quantity));
      setLoading(false);
    };
    fetch();
  }, []);

  return { outletRevenue, lowStockItems, loading };
}
