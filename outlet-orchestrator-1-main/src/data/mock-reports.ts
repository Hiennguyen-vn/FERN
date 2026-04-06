import type { RevenueKPI, RevenueTrend, OutletRevenue, InventoryVarianceSummary, LowStockItem, StockMovementSummary } from '@/types/reports';

export const mockRevenueKPIs: RevenueKPI[] = [
  { label: 'Total Revenue', value: '$148,320', change: 12.4, changeLabel: 'vs last month' },
  { label: 'Total Orders', value: '6,842', change: 8.2, changeLabel: 'vs last month' },
  { label: 'Avg Order Value', value: '$21.68', change: 3.8, changeLabel: 'vs last month' },
  { label: 'Revenue Today', value: '$4,215', change: -2.1, changeLabel: 'vs yesterday' },
];

export const mockRevenueTrend: RevenueTrend[] = [
  { date: '2026-03-28', revenue: 4820, orders: 221 },
  { date: '2026-03-29', revenue: 5140, orders: 238 },
  { date: '2026-03-30', revenue: 6210, orders: 287 },
  { date: '2026-03-31', revenue: 5980, orders: 276 },
  { date: '2026-04-01', revenue: 4650, orders: 215 },
  { date: '2026-04-02', revenue: 4920, orders: 228 },
  { date: '2026-04-03', revenue: 5340, orders: 247 },
  { date: '2026-04-04', revenue: 4215, orders: 195 },
];

export const mockOutletRevenue: OutletRevenue[] = [
  { outletId: 'outlet-001', outletName: 'Downtown Flagship', revenue: 62480, orders: 2890, avgOrderValue: 21.62, rank: 1 },
  { outletId: 'outlet-002', outletName: 'Marina Bay', revenue: 51240, orders: 2340, avgOrderValue: 21.90, rank: 2 },
  { outletId: 'outlet-003', outletName: 'Orchard Central', revenue: 34600, orders: 1612, avgOrderValue: 21.46, rank: 3 },
];

// Extended mock data for enriched revenue dashboard
export const mockOutletTrend: { date: string; downtown: number; marina: number; orchard: number }[] = [
  { date: '2026-03-28', downtown: 2120, marina: 1640, orchard: 1060 },
  { date: '2026-03-29', downtown: 2280, marina: 1720, orchard: 1140 },
  { date: '2026-03-30', downtown: 2740, marina: 2100, orchard: 1370 },
  { date: '2026-03-31', downtown: 2650, marina: 2010, orchard: 1320 },
  { date: '2026-04-01', downtown: 2050, marina: 1580, orchard: 1020 },
  { date: '2026-04-02', downtown: 2180, marina: 1660, orchard: 1080 },
  { date: '2026-04-03', downtown: 2360, marina: 1800, orchard: 1180 },
  { date: '2026-04-04', downtown: 1860, marina: 1420, orchard: 935 },
];

export const mockCategoryRevenue: { category: string; revenue: number; orders: number; pct: number }[] = [
  { category: 'Pizza', revenue: 42840, orders: 2268, pct: 28.9 },
  { category: 'Bowls', revenue: 35280, orders: 1440, pct: 23.8 },
  { category: 'Beverages', revenue: 27540, orders: 3820, pct: 18.6 },
  { category: 'Salads', revenue: 18620, orders: 1250, pct: 12.5 },
  { category: 'Sides', revenue: 14280, orders: 1107, pct: 9.6 },
  { category: 'Burgers', revenue: 5480, orders: 171, pct: 3.7 },
  { category: 'Soups', revenue: 4280, orders: 393, pct: 2.9 },
];

export const mockRevenueInsights: { type: 'positive' | 'warning' | 'neutral'; message: string }[] = [
  { type: 'positive', message: 'Downtown Flagship revenue up 15% week-over-week — highest growth across outlets' },
  { type: 'warning', message: 'Orchard Central average order value declining 3 consecutive days — review menu mix' },
  { type: 'positive', message: 'Beverage category orders up 22% — Iced Latte BOGO promotion driving volume' },
  { type: 'neutral', message: 'Weekend revenue consistently 20-30% higher than weekdays across all outlets' },
  { type: 'warning', message: 'Marina Bay Saturday revenue missed target by $340 — 12 fewer orders than forecast' },
];

export const mockInventoryVariance: InventoryVarianceSummary[] = [
  { outletId: 'outlet-001', outletName: 'Downtown Flagship', totalCounts: 12, varianceItems: 3, varianceValue: -42.50, lastCountDate: '2026-04-03' },
  { outletId: 'outlet-002', outletName: 'Marina Bay', totalCounts: 8, varianceItems: 1, varianceValue: -12.00, lastCountDate: '2026-04-02' },
  { outletId: 'outlet-003', outletName: 'Orchard Central', totalCounts: 6, varianceItems: 4, varianceValue: -67.80, lastCountDate: '2026-03-30' },
];

export const mockLowStockItems: LowStockItem[] = [
  { ingredientName: 'Salmon Fillet', outletName: 'Downtown Flagship', currentQty: 2.5, unit: 'kg', reorderPoint: 5, status: 'low' },
  { ingredientName: 'Truffle Oil', outletName: 'Marina Bay', currentQty: 0, unit: 'ml', reorderPoint: 100, status: 'out' },
  { ingredientName: 'Mozzarella Cheese', outletName: 'Orchard Central', currentQty: 1.2, unit: 'kg', reorderPoint: 3, status: 'low' },
  { ingredientName: 'Espresso Beans', outletName: 'Downtown Flagship', currentQty: 0.3, unit: 'kg', reorderPoint: 2, status: 'low' },
  { ingredientName: 'Heavy Cream', outletName: 'Orchard Central', currentQty: 0, unit: 'L', reorderPoint: 4, status: 'out' },
];

export const mockStockMovements: StockMovementSummary[] = [
  { type: 'Goods Receipt', count: 24, totalQty: 486, period: 'This week' },
  { type: 'Sale Reservation', count: 312, totalQty: 1248, period: 'This week' },
  { type: 'Stock Adjustment', count: 6, totalQty: 18, period: 'This week' },
  { type: 'Waste', count: 8, totalQty: 12, period: 'This week' },
];

// Extended inventory reporting mock data
export const mockVarianceTrend: { date: string; downtown: number; marina: number; orchard: number }[] = [
  { date: '2026-03-28', downtown: -8.20, marina: -3.10, orchard: -12.40 },
  { date: '2026-03-29', downtown: -5.50, marina: -1.80, orchard: -9.60 },
  { date: '2026-03-30', downtown: -12.30, marina: -4.20, orchard: -18.50 },
  { date: '2026-03-31', downtown: -6.80, marina: -2.40, orchard: -14.20 },
  { date: '2026-04-01', downtown: -4.10, marina: 0, orchard: -7.80 },
  { date: '2026-04-02', downtown: -7.60, marina: -1.60, orchard: -5.30 },
  { date: '2026-04-03', downtown: -3.50, marina: 0, orchard: 0 },
  { date: '2026-04-04', downtown: 0, marina: 0, orchard: 0 },
];

export const mockRecentStockCounts: {
  id: string; outletName: string; date: string; status: 'completed' | 'in_progress' | 'pending_review';
  totalItems: number; varianceItems: number; varianceValue: number; countedBy: string;
}[] = [
  { id: 'sc-01', outletName: 'Downtown Flagship', date: '2026-04-03', status: 'completed', totalItems: 28, varianceItems: 3, varianceValue: -42.50, countedBy: 'Sarah Chen' },
  { id: 'sc-02', outletName: 'Marina Bay', date: '2026-04-02', status: 'completed', totalItems: 24, varianceItems: 1, varianceValue: -12.00, countedBy: 'Mike Tan' },
  { id: 'sc-03', outletName: 'Orchard Central', date: '2026-03-30', status: 'pending_review', totalItems: 22, varianceItems: 4, varianceValue: -67.80, countedBy: 'Lisa Wong' },
  { id: 'sc-04', outletName: 'Downtown Flagship', date: '2026-03-28', status: 'completed', totalItems: 28, varianceItems: 2, varianceValue: -18.30, countedBy: 'Sarah Chen' },
];

export const mockMovementByCategory: {
  category: string; receipts: number; sales: number; adjustments: number; waste: number; net: number;
}[] = [
  { category: 'Protein', receipts: 145, sales: 420, adjustments: 3, waste: 5, net: -277 },
  { category: 'Produce', receipts: 180, sales: 310, adjustments: 8, waste: 4, net: -126 },
  { category: 'Dairy', receipts: 62, sales: 198, adjustments: 2, waste: 2, net: -136 },
  { category: 'Dry Goods', receipts: 48, sales: 160, adjustments: 3, waste: 0, net: -115 },
  { category: 'Beverages', receipts: 35, sales: 120, adjustments: 1, waste: 1, net: -85 },
  { category: 'Spices & Oil', receipts: 16, sales: 40, adjustments: 1, waste: 0, net: -25 },
];

export const mockInventoryInsights: { type: 'positive' | 'warning' | 'neutral'; message: string }[] = [
  { type: 'warning', message: 'Orchard Central stock count overdue by 5 days — last count was 2026-03-30' },
  { type: 'warning', message: '2 out-of-stock items (Truffle Oil, Heavy Cream) — may impact menu availability' },
  { type: 'positive', message: 'Downtown Flagship variance trending down — 3 consecutive reductions this week' },
  { type: 'neutral', message: 'Protein category has highest movement volume — 420 sale reservations this week' },
  { type: 'warning', message: 'Total variance value $122.30 across all outlets this period — review waste controls' },
];
