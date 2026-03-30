export interface PaginationState {
  page: number
  size: number
}

export const DEFAULT_PAGINATION: PaginationState = {
  page: 0,
  size: 20,
}
