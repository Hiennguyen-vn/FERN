export function NetworkOfflineBanner() {
  return (
    <div className="banner banner-warning" role="status">
      Network offline. Some write actions may remain blocked until connectivity is restored.
    </div>
  )
}
