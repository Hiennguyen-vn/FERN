import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { exchangeRateApi } from '../api/exchangeRate.api'
import type { UpsertExchangeRatePayload } from '../model/exchangeRate.types'

const EXCHANGE_RATES_KEY = 'exchange-rates'

export function useExchangeRates(params?: { fromCurrency?: string; toCurrency?: string; asOfDate?: string }) {
  return useQuery({
    queryKey: [EXCHANGE_RATES_KEY, params],
    queryFn: () => exchangeRateApi.list(params),
  })
}

export function useUpsertExchangeRate() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: UpsertExchangeRatePayload) => exchangeRateApi.upsert(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [EXCHANGE_RATES_KEY] })
    },
  })
}

export function useDeleteExchangeRate() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (params: { fromCurrency: string; toCurrency: string; effectiveFrom: string }) =>
      exchangeRateApi.remove(params.fromCurrency, params.toCurrency, params.effectiveFrom),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [EXCHANGE_RATES_KEY] })
    },
  })
}
