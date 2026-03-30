export function useConfirmAction(defaultMessage = 'Are you sure?') {
  return (message = defaultMessage) => window.confirm(message)
}
