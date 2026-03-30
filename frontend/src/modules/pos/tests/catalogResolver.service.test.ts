import { describe, expect, it } from 'vitest'
import { resolveMenuItems } from '../services/catalogResolver.service'

describe('catalogResolver.service', () => {
  it('keeps only active available products with effective prices and prefers order-type price over retail', () => {
    const menuItems = resolveMenuItems(
      [
        {
          id: 10,
          productId: 1,
          scopeType: 'GLOBAL',
          scopeId: null,
          priceType: 'RETAIL',
          currencyCode: 'VND',
          priceValue: '45000',
          effectiveFrom: '2026-01-01',
          effectiveTo: null,
        },
        {
          id: 11,
          productId: 1,
          scopeType: 'OUTLET',
          scopeId: 101,
          priceType: 'DINE_IN',
          currencyCode: 'VND',
          priceValue: '48000',
          effectiveFrom: '2026-01-01',
          effectiveTo: null,
        },
        {
          id: 12,
          productId: 2,
          scopeType: 'GLOBAL',
          scopeId: null,
          priceType: 'RETAIL',
          currencyCode: 'VND',
          priceValue: '55000',
          effectiveFrom: '2026-01-01',
          effectiveTo: null,
        },
        {
          id: 13,
          productId: 3,
          scopeType: 'GLOBAL',
          scopeId: null,
          priceType: 'DINE_IN',
          currencyCode: 'VND',
          priceValue: '99000',
          effectiveFrom: '2025-01-01',
          effectiveTo: '2025-12-31',
        },
      ],
      [
        {
          id: 1,
          code: 'PHO-01',
          name: 'Pho bo',
          categoryCode: 'NOODLE',
          status: 'ACTIVE',
          imageUrl: null,
          description: null,
        },
        {
          id: 2,
          code: 'CFE-01',
          name: 'Coffee',
          categoryCode: 'DRINK',
          status: 'ACTIVE',
          imageUrl: null,
          description: null,
        },
        {
          id: 3,
          code: 'OLD-01',
          name: 'Old item',
          categoryCode: 'LEGACY',
          status: 'ACTIVE',
          imageUrl: null,
          description: null,
        },
        {
          id: 4,
          code: 'OFF-01',
          name: 'Disabled item',
          categoryCode: 'DRINK',
          status: 'INACTIVE',
          imageUrl: null,
          description: null,
        },
      ],
      [
        { productId: 1, outletId: 101, available: true },
        { productId: 2, outletId: 101, available: true },
        { productId: 3, outletId: 101, available: true },
        { productId: 4, outletId: 101, available: true },
      ],
      {
        outletId: 101,
        regionId: 1,
        businessDate: '2026-03-29',
        orderType: 'DINE_IN',
      },
    )

    expect(menuItems).toHaveLength(2)
    expect(menuItems[0]).toMatchObject({
      productId: 2,
      priceType: 'RETAIL',
      priceValue: '55000',
    })
    expect(menuItems[1]).toMatchObject({
      productId: 1,
      priceType: 'DINE_IN',
      priceValue: '48000',
      scopeType: 'OUTLET',
    })
  })
})
