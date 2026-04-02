import { useMutation, useQueryClient } from '@tanstack/react-query'
import { generateIdempotencyKey } from '@core/api/idempotency'
import {
  cancelStockAdjustment,
  cancelStockCountSession,
  cancelWasteRecord,
  createStockAdjustment,
  createStockCountSession,
  createWasteRecord,
  postStockAdjustment,
  postStockCountSession,
  postWasteRecord,
  startStockCountSession,
  updateStockCountLines,
} from '../api/inventory.api'
import type {
  CreateStockAdjustmentRequest,
  CreateStockCountSessionRequest,
  CreateWasteRecordRequest,
  UpdateStockCountLinesRequest,
} from '../model/inventory.types'

function invalidateInventoryQueries(queryClient: ReturnType<typeof useQueryClient>) {
  void queryClient.invalidateQueries({ queryKey: ['inventory'] })
  void queryClient.invalidateQueries({ queryKey: ['reports', 'inventory'] })
}

export function useCreateStockAdjustment() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (payload: CreateStockAdjustmentRequest) => createStockAdjustment(payload),
    onSuccess: () => invalidateInventoryQueries(qc),
  })
}

export function usePostStockAdjustment() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => postStockAdjustment(id, generateIdempotencyKey()),
    onSuccess: () => invalidateInventoryQueries(qc),
  })
}

export function useCancelStockAdjustment() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => cancelStockAdjustment(id),
    onSuccess: () => invalidateInventoryQueries(qc),
  })
}

export function useCreateWasteRecord() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (payload: CreateWasteRecordRequest) => createWasteRecord(payload),
    onSuccess: () => invalidateInventoryQueries(qc),
  })
}

export function usePostWasteRecord() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => postWasteRecord(id, generateIdempotencyKey()),
    onSuccess: () => invalidateInventoryQueries(qc),
  })
}

export function useCancelWasteRecord() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => cancelWasteRecord(id),
    onSuccess: () => invalidateInventoryQueries(qc),
  })
}

export function useCreateStockCountSession() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (payload: CreateStockCountSessionRequest) => createStockCountSession(payload),
    onSuccess: () => invalidateInventoryQueries(qc),
  })
}

export function useStartStockCountSession() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => startStockCountSession(id),
    onSuccess: () => invalidateInventoryQueries(qc),
  })
}

export function useUpdateStockCountLines(sessionId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (payload: UpdateStockCountLinesRequest) => updateStockCountLines(sessionId, payload),
    onSuccess: () => invalidateInventoryQueries(qc),
  })
}

export function usePostStockCountSession() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => postStockCountSession(id, generateIdempotencyKey()),
    onSuccess: () => invalidateInventoryQueries(qc),
  })
}

export function useCancelStockCountSession() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => cancelStockCountSession(id),
    onSuccess: () => invalidateInventoryQueries(qc),
  })
}
