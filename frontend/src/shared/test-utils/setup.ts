import { cleanup } from '@testing-library/react'
import '@testing-library/jest-dom'
import { afterEach, vi } from 'vitest'
import { destroyTestQueryClients } from './mockQueryClient'

const storageState = new Map<string, string>()

const memoryStorage: Storage = {
  get length() {
    return storageState.size
  },
  clear() {
    storageState.clear()
  },
  getItem(key: string) {
    return storageState.has(key) ? storageState.get(key)! : null
  },
  key(index: number) {
    return Array.from(storageState.keys())[index] ?? null
  },
  removeItem(key: string) {
    storageState.delete(key)
  },
  setItem(key: string, value: string) {
    storageState.set(String(key), String(value))
  },
}

if (typeof window !== 'undefined') {
  Object.defineProperty(window, 'localStorage', {
    configurable: true,
    value: memoryStorage,
  })
}

Object.defineProperty(globalThis, 'localStorage', {
  configurable: true,
  value: memoryStorage,
})

afterEach(() => {
  cleanup()
  destroyTestQueryClients()
  memoryStorage.clear()
  vi.restoreAllMocks()
  vi.useRealTimers()
})
