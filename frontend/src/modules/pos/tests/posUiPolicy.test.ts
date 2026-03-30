import { describe, expect, it } from 'vitest'
import {
  canCancelOrder,
  canCompleteOrder,
  canEditOrder,
} from '../services/orderUiPolicy.service'
import {
  calculateOutstandingAmount,
  getSuccessfulPaymentTotal,
} from '../services/paymentUiPolicy.service'
import {
  canCloseSession,
  canReconcileSession,
} from '../services/sessionUiPolicy.service'

describe('pos ui policies', () => {
  it('blocks editing and cancel when a successful payment already exists', () => {
    const order = {
      status: 'OPEN',
      totalAmount: '100',
      payments: [
        { status: 'SUCCESS', amount: '40' },
        { status: 'FAILED', amount: '30' },
      ],
    }

    expect(getSuccessfulPaymentTotal(order as any)).toBe(40)
    expect(calculateOutstandingAmount(order as any)).toBe(60)
    expect(canEditOrder(order as any)).toBe(false)
    expect(canCancelOrder(order as any, true)).toBe(false)
    expect(canCompleteOrder(order as any, true)).toBe(false)
  })

  it('allows complete only when successful payments cover the full total', () => {
    const order = {
      status: 'OPEN',
      totalAmount: '100',
      payments: [{ status: 'SUCCESS', amount: '100' }],
    }

    expect(calculateOutstandingAmount(order as any)).toBe(0)
    expect(canCompleteOrder(order as any, true)).toBe(true)
  })

  it('applies session lifecycle rules for close and reconcile', () => {
    expect(canCloseSession({ status: 'OPEN' } as any, true)).toBe(true)
    expect(canCloseSession({ status: 'CLOSED' } as any, true)).toBe(false)
    expect(canReconcileSession({ status: 'CLOSED' } as any, true)).toBe(true)
    expect(canReconcileSession({ status: 'OPEN' } as any, true)).toBe(false)
  })
})
