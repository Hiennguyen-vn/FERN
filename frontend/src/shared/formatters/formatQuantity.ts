export function formatQuantity(value: number | string | null | undefined, maximumFractionDigits = 4) {
  const quantity = Number(value ?? 0)

  return new Intl.NumberFormat('vi-VN', {
    maximumFractionDigits,
  }).format(quantity)
}
