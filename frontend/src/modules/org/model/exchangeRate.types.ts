export interface ExchangeRate {
  fromCurrencyCode: string
  toCurrencyCode: string
  rate: number
  effectiveFrom: string
  effectiveTo: string | null
}

export interface UpsertExchangeRatePayload {
  fromCurrencyCode: string
  toCurrencyCode: string
  rate: number
  effectiveFrom: string
  effectiveTo?: string | null
}
