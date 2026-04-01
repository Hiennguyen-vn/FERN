import { gatewayClient } from '@core/api/gatewayClient'
import type { ExchangeRate, UpsertExchangeRatePayload } from '../model/exchangeRate.types'

export const exchangeRateApi = {
  list(params?: { fromCurrency?: string; toCurrency?: string; asOfDate?: string }) {
    return gatewayClient
      .get<ExchangeRate[]>('/exchange-rates', { params })
      .then((r) => r.data)
  },

  upsert(payload: UpsertExchangeRatePayload) {
    return gatewayClient
      .post<ExchangeRate>('/exchange-rates', payload)
      .then((r) => r.data)
  },

  remove(fromCurrency: string, toCurrency: string, effectiveFrom: string) {
    return gatewayClient
      .delete('/exchange-rates', { params: { fromCurrency, toCurrency, effectiveFrom } })
      .then(() => undefined)
  },
}
