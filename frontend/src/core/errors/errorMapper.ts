import { ApiError } from '@core/api/apiError'
import { errorMessages } from './errorMessages'

export function mapErrorToMessage(error: unknown): string {
  if (error instanceof ApiError && error.code) {
    return errorMessages[error.code as keyof typeof errorMessages] ?? error.message
  }

  if (error instanceof Error) {
    return error.message
  }

  return errorMessages.UNKNOWN_ERROR
}
