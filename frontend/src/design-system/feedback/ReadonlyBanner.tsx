interface ReadonlyBannerProps {
  message?: string
}

export function ReadonlyBanner({ message = 'Màn hình này đang ở chế độ chỉ đọc.' }: ReadonlyBannerProps) {
  return (
    <div className="banner" role="status">
      {message}
    </div>
  )
}
