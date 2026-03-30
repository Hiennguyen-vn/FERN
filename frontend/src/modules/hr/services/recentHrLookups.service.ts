import type { HrContract, HrEmployee, RecentHrContractLookup } from '../model/hr.types'

const EMPLOYEE_STORAGE_KEY = 'hr.recent-employees'
const CONTRACT_STORAGE_KEY = 'hr.recent-contracts'
const MAX_ITEMS = 12

function readStorage<T>(key: string): T[] {
  if (typeof window === 'undefined' || !window.localStorage) {
    return []
  }

  const raw = window.localStorage.getItem(key)
  if (!raw) {
    return []
  }

  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function writeStorage<T extends { id?: number; contract?: { id: number } }>(key: string, value: T, matcher: (item: T) => boolean) {
  if (typeof window === 'undefined' || !window.localStorage) {
    return
  }

  const items = readStorage<T>(key).filter((item) => !matcher(item))
  items.unshift(value)
  window.localStorage.setItem(key, JSON.stringify(items.slice(0, MAX_ITEMS)))
}

export function loadRecentHrEmployees() {
  return readStorage<HrEmployee>(EMPLOYEE_STORAGE_KEY)
}

export function saveRecentHrEmployee(employee: HrEmployee) {
  writeStorage(EMPLOYEE_STORAGE_KEY, employee, (item) => item.id === employee.id)
}

export function clearRecentHrEmployees() {
  if (typeof window === 'undefined' || !window.localStorage) {
    return
  }

  window.localStorage.removeItem(EMPLOYEE_STORAGE_KEY)
}

export function loadRecentHrContracts() {
  return readStorage<RecentHrContractLookup>(CONTRACT_STORAGE_KEY)
}

export function saveRecentHrContract(contract: HrContract, context: { employeeCode?: string | null; employeeName?: string | null }) {
  const payload: RecentHrContractLookup = {
    contract,
    employeeCode: context.employeeCode,
    employeeId: contract.employeeId,
    employeeName: context.employeeName,
  }

  writeStorage(CONTRACT_STORAGE_KEY, payload, (item) => item.contract.id === contract.id)
}

export function findRecentHrContract(contractId: number) {
  return loadRecentHrContracts().find((item) => item.contract.id === contractId) ?? null
}

export function clearRecentHrContracts() {
  if (typeof window === 'undefined' || !window.localStorage) {
    return
  }

  window.localStorage.removeItem(CONTRACT_STORAGE_KEY)
}
