import type { CRMCustomer, PurchaseHistory, LoyaltyProgram, TierConfig, Reward, Voucher } from '@/types/crm';

export const mockTierConfigs: TierConfig[] = [
  { tier: 'bronze', minPoints: 0, benefits: ['1x points earning', 'Birthday reward'], pointsMultiplier: 1, color: '#CD7F32' },
  { tier: 'silver', minPoints: 500, benefits: ['1.5x points earning', 'Birthday reward', '5% member discount'], pointsMultiplier: 1.5, color: '#C0C0C0' },
  { tier: 'gold', minPoints: 2000, benefits: ['2x points earning', 'Birthday reward', '10% member discount', 'Priority seating'], pointsMultiplier: 2, color: '#FFD700' },
  { tier: 'platinum', minPoints: 5000, benefits: ['3x points earning', 'Birthday reward', '15% member discount', 'Priority seating', 'Free delivery', 'Exclusive events'], pointsMultiplier: 3, color: '#E5E4E2' },
];

export const mockLoyaltyProgram: LoyaltyProgram = {
  id: 'lp-001',
  name: 'F&B Rewards',
  tiers: mockTierConfigs,
  pointsPerCurrency: 1,
  isActive: true,
};

export const mockCRMCustomers: CRMCustomer[] = [
  { id: 'cust-001', name: 'Nguyễn Minh Anh', phone: '0901234567', email: 'minhanh@email.com', memberCode: 'MBR-0001', loyaltyTier: 'gold', loyaltyPoints: 2450, lifetimePoints: 4200, totalSpend: 4200000, visitCount: 48, averageOrderValue: 87500, lastVisit: '2026-04-04', joinedAt: '2025-06-15', outletName: 'Downtown Flagship', tags: ['regular', 'coffee-lover'], notes: 'Prefers oat milk' },
  { id: 'cust-002', name: 'Trần Hải Long', phone: '0912345678', email: 'hailong@email.com', memberCode: 'MBR-0002', loyaltyTier: 'platinum', loyaltyPoints: 5800, lifetimePoints: 12500, totalSpend: 12500000, visitCount: 120, averageOrderValue: 104167, lastVisit: '2026-04-05', joinedAt: '2024-11-01', outletName: 'Riverside Branch', tags: ['vip', 'business-lunch'], notes: 'Usually brings colleagues' },
  { id: 'cust-003', name: 'Lê Thị Hương', phone: '0923456789', memberCode: 'MBR-0003', loyaltyTier: 'silver', loyaltyPoints: 780, lifetimePoints: 1500, totalSpend: 1500000, visitCount: 22, averageOrderValue: 68182, lastVisit: '2026-03-28', joinedAt: '2025-09-10', outletName: 'Mall Kiosk A', tags: ['weekend'], },
  { id: 'cust-004', name: 'Phạm Đức Thắng', phone: '0934567890', email: 'ducthang@email.com', memberCode: 'MBR-0004', loyaltyTier: 'bronze', loyaltyPoints: 120, lifetimePoints: 320, totalSpend: 320000, visitCount: 5, averageOrderValue: 64000, lastVisit: '2026-04-01', joinedAt: '2026-02-20', outletName: 'Uptown Express', tags: ['new'], },
  { id: 'cust-005', name: 'Hoàng Mai Linh', phone: '0945678901', email: 'mailinh@email.com', memberCode: 'MBR-0005', loyaltyTier: 'gold', loyaltyPoints: 3100, lifetimePoints: 6800, totalSpend: 6800000, visitCount: 85, averageOrderValue: 80000, lastVisit: '2026-04-03', joinedAt: '2025-03-01', outletName: 'Station Café', tags: ['regular', 'tea-lover'], },
  { id: 'cust-006', name: 'Vũ Quang Huy', phone: '0956789012', memberCode: 'MBR-0006', loyaltyTier: 'silver', loyaltyPoints: 950, lifetimePoints: 1800, totalSpend: 1800000, visitCount: 30, averageOrderValue: 60000, lastVisit: '2026-03-30', joinedAt: '2025-07-20', outletName: 'Harbor View', tags: ['family'], },
];

export const mockPurchaseHistory: PurchaseHistory[] = [
  { id: 'ph-001', customerId: 'cust-001', orderNumber: 'SO-2026-0401', outletName: 'Downtown Flagship', date: '2026-04-04', items: ['Cappuccino', 'Croissant'], total: 95000, pointsEarned: 95, pointsRedeemed: 0 },
  { id: 'ph-002', customerId: 'cust-001', orderNumber: 'SO-2026-0388', outletName: 'Downtown Flagship', date: '2026-04-02', items: ['Latte (Oat)', 'Avocado Toast'], total: 125000, pointsEarned: 125, pointsRedeemed: 0 },
  { id: 'ph-003', customerId: 'cust-002', orderNumber: 'SO-2026-0405', outletName: 'Riverside Branch', date: '2026-04-05', items: ['Business Lunch Set A', 'Iced Tea x3'], total: 450000, pointsEarned: 450, pointsRedeemed: 200 },
  { id: 'ph-004', customerId: 'cust-002', orderNumber: 'SO-2026-0399', outletName: 'Riverside Branch', date: '2026-04-03', items: ['Steak Set', 'Sparkling Water'], total: 280000, pointsEarned: 280, pointsRedeemed: 0 },
  { id: 'ph-005', customerId: 'cust-005', orderNumber: 'SO-2026-0390', outletName: 'Station Café', date: '2026-04-03', items: ['Jasmine Tea', 'Matcha Cake'], total: 85000, pointsEarned: 85, pointsRedeemed: 50 },
];

export const mockRewards: Reward[] = [
  { id: 'rwd-001', name: 'Free Coffee', type: 'free_item', value: 1, pointsCost: 200, description: 'Redeem for any regular coffee', isActive: true, validFrom: '2026-01-01', validUntil: '2026-12-31', redemptionCount: 342, maxRedemptions: 1000 },
  { id: 'rwd-002', name: '10% Off Order', type: 'discount_percent', value: 10, pointsCost: 150, description: '10% discount on total order', isActive: true, validFrom: '2026-01-01', validUntil: '2026-06-30', redemptionCount: 185 },
  { id: 'rwd-003', name: '50K Voucher', type: 'discount_fixed', value: 50000, pointsCost: 500, description: '50,000đ off your next order', isActive: true, validFrom: '2026-03-01', validUntil: '2026-09-30', redemptionCount: 78, maxRedemptions: 500 },
  { id: 'rwd-004', name: 'Double Points Day', type: 'points_multiplier', value: 2, pointsCost: 300, description: 'Earn double points on your next visit', isActive: false, validFrom: '2026-01-01', validUntil: '2026-03-31', redemptionCount: 45 },
];

export const mockVouchers: Voucher[] = [
  { id: 'v-001', code: 'COFFEE-X8K2', customerId: 'cust-001', customerName: 'Nguyễn Minh Anh', rewardId: 'rwd-001', rewardName: 'Free Coffee', status: 'active', discountValue: 1, discountType: 'fixed', issuedAt: '2026-04-01', expiresAt: '2026-04-30' },
  { id: 'v-002', code: 'DISC10-P3M9', customerId: 'cust-002', customerName: 'Trần Hải Long', rewardId: 'rwd-002', rewardName: '10% Off Order', status: 'used', discountValue: 10, discountType: 'percent', issuedAt: '2026-03-15', expiresAt: '2026-04-15', usedAt: '2026-03-28', usedAtOutlet: 'Riverside Branch' },
  { id: 'v-003', code: 'VCH50K-A1B2', customerId: 'cust-005', customerName: 'Hoàng Mai Linh', rewardId: 'rwd-003', rewardName: '50K Voucher', status: 'active', discountValue: 50000, discountType: 'fixed', issuedAt: '2026-04-02', expiresAt: '2026-05-02' },
  { id: 'v-004', code: 'COFFEE-Y9L3', customerId: 'cust-003', customerName: 'Lê Thị Hương', rewardId: 'rwd-001', rewardName: 'Free Coffee', status: 'expired', discountValue: 1, discountType: 'fixed', issuedAt: '2026-02-01', expiresAt: '2026-02-28' },
];

export const crmStats = {
  totalMembers: 1248,
  newThisMonth: 87,
  activeRate: 72,
  totalPointsCirculating: 485000,
  tierDistribution: { bronze: 620, silver: 380, gold: 195, platinum: 53 },
  avgLifetimeValue: 2850000,
};
