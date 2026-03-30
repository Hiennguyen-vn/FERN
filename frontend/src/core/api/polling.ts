export function getPollingInterval(enabled: boolean, intervalMs = 5_000): number | false {
  return enabled ? intervalMs : false
}
