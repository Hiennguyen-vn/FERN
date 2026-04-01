import { afterEach, describe, expect, it, vi } from 'vitest'
import { gatewayClient } from '@core/api/gatewayClient'
import { orgApi } from '../api/org.api'
import { exchangeRateApi } from '../api/exchangeRate.api'

describe('org.api', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('sends region create and update payloads using backend-supported field names', async () => {
    const postSpy = vi.spyOn(gatewayClient, 'post').mockResolvedValue({ data: { id: 1 } } as any)
    const patchSpy = vi.spyOn(gatewayClient, 'patch').mockResolvedValue({ data: { id: 1 } } as any)

    await orgApi.createRegion({
      code: 'R-SOUTH',
      parentRegionId: 1,
      currencyCode: 'VND',
      name: 'South',
      taxCode: 'TAX-01',
      timezoneName: 'Asia/Ho_Chi_Minh',
    })
    await orgApi.updateRegion(1, {
      parentRegionId: 2,
      currencyCode: 'USD',
      name: 'South 2',
      taxCode: 'TAX-02',
      timezoneName: 'Asia/Singapore',
    })

    expect(postSpy).toHaveBeenCalledWith('/regions', {
      code: 'R-SOUTH',
      parentRegionId: 1,
      currencyCode: 'VND',
      name: 'South',
      taxCode: 'TAX-01',
      timezoneName: 'Asia/Ho_Chi_Minh',
    })
    expect(patchSpy).toHaveBeenCalledWith('/regions/1', {
      parentRegionId: 2,
      currencyCode: 'USD',
      name: 'South 2',
      taxCode: 'TAX-02',
      timezoneName: 'Asia/Singapore',
    })
  })
})

/**
 * Exchange rate API contract tests.
 *
 * Backend reference: ExchangeRateController (org-service)
 *   POST   /exchange-rates       — upsert, body: { fromCurrencyCode, toCurrencyCode, rate, effectiveFrom, effectiveTo }
 *   DELETE /exchange-rates       — query params: fromCurrency, toCurrency, effectiveFrom
 *   GET    /exchange-rates       — query params: fromCurrency, toCurrency, asOfDate
 *
 * Critical: DELETE uses query params, NOT a request body. A body-based delete
 * would be silently ignored by the HTTP client and fail at the backend.
 */
describe('exchangeRateApi', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('sends DELETE with fromCurrency, toCurrency, effectiveFrom as query params — not body', async () => {
    const deleteSpy = vi.spyOn(gatewayClient, 'delete').mockResolvedValue({ data: undefined } as any)

    await exchangeRateApi.remove('USD', 'VND', '2026-04-01')

    expect(deleteSpy).toHaveBeenCalledWith('/exchange-rates', {
      params: {
        fromCurrency: 'USD',
        toCurrency: 'VND',
        effectiveFrom: '2026-04-01',
      },
    })
  })

  it('sends POST upsert with all required body fields', async () => {
    const postSpy = vi.spyOn(gatewayClient, 'post').mockResolvedValue({
      data: {
        fromCurrencyCode: 'USD',
        toCurrencyCode: 'VND',
        rate: 25350,
        effectiveFrom: '2026-04-01',
        effectiveTo: null,
      },
    } as any)

    await exchangeRateApi.upsert({
      fromCurrencyCode: 'USD',
      toCurrencyCode: 'VND',
      rate: 25350,
      effectiveFrom: '2026-04-01',
      effectiveTo: null,
    })

    expect(postSpy).toHaveBeenCalledWith('/exchange-rates', {
      fromCurrencyCode: 'USD',
      toCurrencyCode: 'VND',
      rate: 25350,
      effectiveFrom: '2026-04-01',
      effectiveTo: null,
    })
  })

  it('sends GET list with optional filter params', async () => {
    const getSpy = vi.spyOn(gatewayClient, 'get').mockResolvedValue({ data: [] } as any)

    await exchangeRateApi.list({ fromCurrency: 'USD', toCurrency: 'VND' })

    expect(getSpy).toHaveBeenCalledWith('/exchange-rates', {
      params: { fromCurrency: 'USD', toCurrency: 'VND' },
    })
  })
})
