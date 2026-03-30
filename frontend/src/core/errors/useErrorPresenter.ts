import { mapErrorToMessage } from './errorMapper'

export function useErrorPresenter() {
  return {
    presentError: (error: unknown) => mapErrorToMessage(error),
  }
}
