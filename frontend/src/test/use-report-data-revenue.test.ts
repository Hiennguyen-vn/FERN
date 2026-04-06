import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { useReportData } from '@/hooks/use-dashboard-data';
import { revenueOutletToday } from '@/lib/api/reports';
import { listOutlets } from '@/lib/api/org';
import { listIngredients } from '@/lib/api/catalog';
import { listStockBalances } from '@/lib/api/inventory';

vi.mock('@/lib/api/reports', () => ({
  revenueOutletToday: vi.fn(),
}));

vi.mock('@/lib/api/org', () => ({
  listOutlets: vi.fn(),
}));

vi.mock('@/lib/api/catalog', () => ({
  listIngredients: vi.fn(),
}));

vi.mock('@/lib/api/inventory', () => ({
  listStockBalances: vi.fn(),
}));

describe('useReportData revenue aggregate', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(listOutlets).mockResolvedValue({
      items: [
        { id: 101, name: 'Downtown', regionId: 1, code: 'DT', status: 'ACTIVE', createdAt: '', updatedAt: '' },
        { id: 102, name: 'Riverside', regionId: 1, code: 'RV', status: 'ACTIVE', createdAt: '', updatedAt: '' },
      ],
      page: 0,
      size: 1000,
      hasMore: false,
    } as any);
    vi.mocked(revenueOutletToday).mockResolvedValue([
      {
        outletId: 101,
        sessionId: 9001,
        sessionStatus: 'OPEN',
        currencyCode: 'VND',
        totalOrders: 5,
        completed: 4,
        open: 1,
        cancelled: 0,
        totalRevenue: '125.50',
        cashCollected: '40.00',
        nonCashCollected: '85.50',
      },
      {
        outletId: 102,
        sessionId: null,
        sessionStatus: 'NO_SESSION',
        currencyCode: 'VND',
        totalOrders: 2,
        completed: 2,
        open: 0,
        cancelled: 0,
        totalRevenue: '40.00',
        cashCollected: '10.00',
        nonCashCollected: '30.00',
      },
    ] as any);
    vi.mocked(listIngredients).mockResolvedValue([]);
    vi.mocked(listStockBalances).mockResolvedValue({ items: [], page: 0, size: 500, hasMore: false } as any);
  });

  it('uses report endpoint aggregate and preserves outletRevenue shape', async () => {
    const { result } = renderHook(() => useReportData());

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    expect(revenueOutletToday).toHaveBeenCalledWith([101, 102]);
    expect(result.current.outletRevenue).toEqual([
      {
        outletId: '101',
        outletName: 'Downtown',
        revenue: 125.5,
        orders: 5,
        avgOrderValue: 25.1,
      },
      {
        outletId: '102',
        outletName: 'Riverside',
        revenue: 40,
        orders: 2,
        avgOrderValue: 20,
      },
    ]);
  });

  it('returns controlled empty revenue list when no outlets returned by scope', async () => {
    vi.mocked(listOutlets).mockResolvedValue({ items: [], page: 0, size: 1000, hasMore: false } as any);

    const { result } = renderHook(() => useReportData());
    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    expect(revenueOutletToday).not.toHaveBeenCalled();
    expect(result.current.outletRevenue).toEqual([]);
  });
});
