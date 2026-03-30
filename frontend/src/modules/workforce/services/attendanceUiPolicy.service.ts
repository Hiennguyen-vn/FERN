export function canReviewAttendance(status: string) {
  return status.toUpperCase() === 'PENDING'
}

export function getAttendanceStatusTone(status: string): 'neutral' | 'success' | 'warning' | 'danger' {
  switch (status.toUpperCase()) {
    case 'APPROVED':
      return 'success'
    case 'PENDING':
      return 'warning'
    case 'REJECTED':
      return 'danger'
    default:
      return 'neutral'
  }
}
