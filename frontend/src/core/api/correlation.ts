import { v4 as uuidv4 } from 'uuid'

// A small utility to retrieve an existing correlation ID or generate a new one
let currentCorrelationId: string | null = null

export function getCorrelationId(): string {
  if (!currentCorrelationId) {
    currentCorrelationId = uuidv4()
  }
  return currentCorrelationId
}

export function resetCorrelationId(): void {
  currentCorrelationId = null
}
