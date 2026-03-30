interface PermissionDeniedInlineProps {
  message?: string
  title?: string
}

export function PermissionDeniedInline({
  message = 'Bạn không có quyền thực hiện thao tác này trong phạm vi, trạng thái hoặc vai trò hiện tại.',
  title = 'Permission denied',
}: PermissionDeniedInlineProps) {
  return (
    <div className="inline-banner inline-banner-danger" role="status">
      <div className="page-stack">
        <strong>{title}</strong>
        <span>{message}</span>
      </div>
    </div>
  )
}
