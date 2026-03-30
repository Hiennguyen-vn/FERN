export function formatMoney(value: number | string | null | undefined, currencyCode = 'VND') {
  const amount = Number(value ?? 0)

  try {
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: currencyCode,
      maximumFractionDigits: currencyCode === 'VND' ? 0 : 2,
    }).format(amount)
  } catch {
    return `${amount.toFixed(2)} ${currencyCode}`
  }
}
