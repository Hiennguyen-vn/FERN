import { useState, useEffect, useCallback } from 'react';
import { listOutlets } from '@/lib/api/org';
import { listSessions, listOrdersBySession, listOutletStatsToday } from '@/lib/api/pos';
import { listStockBalances } from '@/lib/api/inventory';
import { listIngredients } from '@/lib/api/catalog';
import { revenueOutletToday } from '@/lib/api/reports';
import type { ShellScope } from '@/types/shell';

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

const DEFAULT_SCOPE: ShellScope = { level: 'system' };

function toNumber(value: unknown): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function normalizeStatus(value: string | undefined): string {
  return String(value || '').toLowerCase();
}

function stableScopeKey(scope: ShellScope): string {
  return `${scope.level}:${scope.regionId ?? ''}:${scope.outletId ?? ''}`;
}

async function resolveOutlets(scope: ShellScope) {
  const page = await listOutlets({
    regionId: scope.level === 'region' ? scope.regionId : undefined,
    size: 1000,
  });

  let items = page.items;
  if (scope.level === 'outlet' && scope.outletId) {
    items = items.filter((item) => item.id === scope.outletId);
  }

  const outletIds = items.map((item) => item.id);
  const outletNameMap = new Map<number, string>(items.map((item) => [item.id, item.name]));
  return { outletIds, outletNameMap };
}

async function buildLowStockAlerts(outletIds: number[], outletNameMap: Map<number, string>): Promise<LowStockAlert[]> {
  if (!outletIds.length) return [];

  const [ingredients, balancePages] = await Promise.all([
    listIngredients(1000),
    Promise.all(outletIds.map((outletId) => listStockBalances({ outletId, page: 0, size: 500 }))),
  ]);

  const ingredientMap = new Map(ingredients.map((item) => [item.id, item]));
  const alerts: LowStockAlert[] = [];

  balancePages.forEach((page) => {
    page.items.forEach((balance) => {
      const ingredient = ingredientMap.get(balance.ingredientId);
      if (!ingredient) return;

      const qty = toNumber(balance.qtyOnHand);
      const reorder = toNumber(ingredient.minStockLevel);
      if (qty > reorder) return;

      alerts.push({
        itemName: ingredient.name,
        category: ingredient.categoryCode || null,
        quantity: qty,
        reorderLevel: reorder,
        outletName: outletNameMap.get(balance.outletId) || `Outlet #${balance.outletId}`,
        critical: qty === 0 || (reorder > 0 && qty <= reorder * 0.3),
      });
    });
  });

  return alerts.sort((a, b) => a.quantity - b.quantity);
}

export function useDashboardData(scope: ShellScope = DEFAULT_SCOPE) {
  const [kpis, setKpis] = useState<DashboardKPIs>({
    totalRevenue: 0,
    totalOrders: 0,
    completedOrders: 0,
    avgOrderValue: 0,
    activeSessions: 0,
    lowStockCount: 0,
    outOfStockCount: 0,
    pendingOrders: 0,
  });
  const [recentOrders, setRecentOrders] = useState<RecentOrder[]>([]);
  const [lowStock, setLowStock] = useState<LowStockAlert[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const { outletIds, outletNameMap } = await resolveOutlets(scope);
      if (!outletIds.length) {
        setKpis({
          totalRevenue: 0,
          totalOrders: 0,
          completedOrders: 0,
          avgOrderValue: 0,
          activeSessions: 0,
          lowStockCount: 0,
          outOfStockCount: 0,
          pendingOrders: 0,
        });
        setRecentOrders([]);
        setLowStock([]);
        setLoading(false);
        return;
      }

      const [statsRows, sessionRowsByOutlet, alerts] = await Promise.all([
        listOutletStatsToday(outletIds),
        Promise.all(outletIds.map((outletId) => listSessions(outletId, { limit: 20 }))),
        buildLowStockAlerts(outletIds, outletNameMap),
      ]);

      const allSessions = sessionRowsByOutlet.flat();
      const sessionForOrders = allSessions
        .sort((a, b) => new Date(b.openedAt).getTime() - new Date(a.openedAt).getTime())
        .slice(0, 20);

      const ordersBySession = await Promise.all(sessionForOrders.map((session) => listOrdersBySession(session.id, 50)));
      const allOrders = ordersBySession
        .flat()
        .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());

      const totalRevenue = statsRows.reduce((sum, row) => sum + toNumber(row.totalRevenue), 0);
      const totalOrders = statsRows.reduce((sum, row) => sum + toNumber(row.totalOrders), 0);
      const completedOrders = statsRows.reduce((sum, row) => sum + toNumber(row.completed), 0);
      const pendingOrders = statsRows.reduce((sum, row) => sum + toNumber(row.open), 0);
      const activeSessions = statsRows.filter((row) => normalizeStatus(row.sessionStatus) === 'open').length;
      const outOfStockCount = alerts.filter((item) => item.quantity === 0).length;

      setKpis({
        totalRevenue,
        totalOrders,
        completedOrders,
        avgOrderValue: completedOrders > 0 ? totalRevenue / completedOrders : 0,
        activeSessions,
        lowStockCount: alerts.length,
        outOfStockCount,
        pendingOrders,
      });

      setRecentOrders(
        allOrders.slice(0, 10).map((order) => ({
          id: String(order.id),
          order_number: order.orderNumber,
          total: toNumber(order.totalAmount),
          status: normalizeStatus(order.status),
          order_type: order.orderType || null,
          table_number: order.tableName || null,
          created_at: order.createdAt,
        }))
      );

      setLowStock(alerts.slice(0, 10));
    } catch (err) {
      console.error('Dashboard fetch error:', err);
      setKpis({
        totalRevenue: 0,
        totalOrders: 0,
        completedOrders: 0,
        avgOrderValue: 0,
        activeSessions: 0,
        lowStockCount: 0,
        outOfStockCount: 0,
        pendingOrders: 0,
      });
      setRecentOrders([]);
      setLowStock([]);
    }
    setLoading(false);
  }, [scope]);

  useEffect(() => {
    void fetchData();
  }, [fetchData]);

  return { kpis, recentOrders, lowStock, loading, refresh: fetchData };
}

export function useReportData(scope: ShellScope = DEFAULT_SCOPE) {
  const scopeKey = stableScopeKey(scope);
  const [outletRevenue, setOutletRevenue] = useState<
    { outletId: string; outletName: string; revenue: number; orders: number; avgOrderValue: number }[]
  >([]);
  const [lowStockItems, setLowStockItems] = useState<LowStockAlert[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetch = async () => {
      setLoading(true);
      try {
        const { outletIds, outletNameMap } = await resolveOutlets(scope);
        if (!outletIds.length) {
          setOutletRevenue([]);
          setLowStockItems([]);
          setLoading(false);
          return;
        }

        const [revenueRows, alerts] = await Promise.all([
          revenueOutletToday(outletIds),
          buildLowStockAlerts(outletIds, outletNameMap),
        ]);

        const outletRev = revenueRows
          .map((row) => {
            const outletId = String(row.outletId);
            const revenue = toNumber(row.totalRevenue);
            const orders = toNumber(row.totalOrders);
            return {
              outletId,
              outletName: outletNameMap.get(row.outletId) || `Outlet #${outletId}`,
              revenue,
              orders,
              avgOrderValue: orders > 0 ? revenue / orders : 0,
            };
          })
          .sort((a, b) => b.revenue - a.revenue);

        setOutletRevenue(outletRev);
        setLowStockItems(alerts);
      } catch (error) {
        console.error('Report data fetch error:', error);
        setOutletRevenue([]);
        setLowStockItems([]);
      }
      setLoading(false);
    };

    void fetch();
  }, [scope, scopeKey]);

  return { outletRevenue, lowStockItems, loading };
}
