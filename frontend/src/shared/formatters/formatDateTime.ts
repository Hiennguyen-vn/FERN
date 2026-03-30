export function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return 'N/A'
  }

  return new Date(value).toLocaleString('vi-VN')
}
