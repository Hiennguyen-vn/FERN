import type { Promotion, PromotionPerformance } from '@/types/promotions';

export const mockPromotions: Promotion[] = [
  {
    id: 'promo-001', name: 'Happy Hour Drinks', code: 'HAPPYHOUR', type: 'happy_hour', status: 'active',
    description: 'All beverages 30% off from 2-5 PM daily',
    discountValue: 30, discountType: 'percent', minOrderValue: 0, maxDiscount: 100000,
    scope: 'all', appliedOutlets: [], appliedRegions: [],
    startDate: '2026-03-01', endDate: '2026-06-30',
    activeDays: ['mon', 'tue', 'wed', 'thu', 'fri'], startTime: '14:00', endTime: '17:00',
    maxUsage: 5000, usageCount: 1823, maxPerCustomer: 1,
    applicableCategories: ['Beverages'], applicableItems: [],
    totalRevenue: 45800000, totalDiscount: 19600000, ordersUsed: 1823,
    createdAt: '2026-02-15', createdBy: 'Sarah Chen',
  },
  {
    id: 'promo-002', name: 'Combo Lunch Set', code: 'LUNCHSET', type: 'combo', status: 'active',
    description: 'Main + Side + Drink for 99K (save 30K)',
    discountValue: 30000, discountType: 'fixed',
    scope: 'region', appliedOutlets: [], appliedRegions: ['Central Region'],
    startDate: '2026-04-01', endDate: '2026-04-30',
    activeDays: ['mon', 'tue', 'wed', 'thu', 'fri'], startTime: '11:00', endTime: '14:00',
    usageCount: 456, maxPerCustomer: 2,
    applicableCategories: ['Lunch Sets'], applicableItems: [],
    totalRevenue: 45144000, totalDiscount: 13680000, ordersUsed: 456,
    createdAt: '2026-03-25', createdBy: 'Marcus Rivera',
  },
  {
    id: 'promo-003', name: 'Buy 1 Get 1 Pastries', code: 'BOGO-PASTRY', type: 'bogo', status: 'scheduled',
    description: 'Buy any pastry, get the second one free',
    discountValue: 100, discountType: 'percent',
    scope: 'outlet', appliedOutlets: ['Downtown Flagship', 'Mall Kiosk A'], appliedRegions: [],
    startDate: '2026-04-10', endDate: '2026-04-20',
    activeDays: ['sat', 'sun'],
    usageCount: 0, maxUsage: 200,
    applicableCategories: ['Pastries'], applicableItems: [],
    totalRevenue: 0, totalDiscount: 0, ordersUsed: 0,
    createdAt: '2026-04-01', createdBy: 'Sarah Chen',
  },
  {
    id: 'promo-004', name: 'Weekend Family Discount', code: 'FAMILY15', type: 'discount_percent', status: 'active',
    description: '15% off orders above 300K on weekends',
    discountValue: 15, discountType: 'percent', minOrderValue: 300000, maxDiscount: 150000,
    scope: 'all', appliedOutlets: [], appliedRegions: [],
    startDate: '2026-03-15', endDate: '2026-05-31',
    activeDays: ['sat', 'sun'],
    usageCount: 312,
    applicableCategories: [], applicableItems: [],
    totalRevenue: 124800000, totalDiscount: 18720000, ordersUsed: 312,
    createdAt: '2026-03-10', createdBy: 'Sarah Chen',
  },
  {
    id: 'promo-005', name: 'New Member Welcome', code: 'WELCOME20', type: 'discount_fixed', status: 'paused',
    description: '20K off first order for new loyalty members',
    discountValue: 20000, discountType: 'fixed', minOrderValue: 50000,
    scope: 'all', appliedOutlets: [], appliedRegions: [],
    startDate: '2026-01-01', endDate: '2026-12-31',
    activeDays: ['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun'],
    usageCount: 587, maxPerCustomer: 1,
    applicableCategories: [], applicableItems: [],
    totalRevenue: 41090000, totalDiscount: 11740000, ordersUsed: 587,
    createdAt: '2025-12-20', createdBy: 'Sarah Chen',
  },
];

export const mockPromotionPerformance: PromotionPerformance[] = [
  { promotionId: 'promo-001', date: '2026-04-01', ordersUsed: 65, revenue: 1625000, discountGiven: 695000, averageOrderValue: 25000 },
  { promotionId: 'promo-001', date: '2026-04-02', ordersUsed: 72, revenue: 1800000, discountGiven: 770000, averageOrderValue: 25000 },
  { promotionId: 'promo-001', date: '2026-04-03', ordersUsed: 58, revenue: 1450000, discountGiven: 621000, averageOrderValue: 25000 },
  { promotionId: 'promo-001', date: '2026-04-04', ordersUsed: 81, revenue: 2025000, discountGiven: 868000, averageOrderValue: 25000 },
  { promotionId: 'promo-004', date: '2026-04-05', ordersUsed: 45, revenue: 18000000, discountGiven: 2700000, averageOrderValue: 400000 },
];

export const promotionStats = {
  activePromotions: 3,
  totalDiscountThisMonth: 8500000,
  ordersWithPromo: 892,
  promoUsageRate: 23.5,
};
