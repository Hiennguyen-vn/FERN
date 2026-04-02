import type { FernPrincipal } from '@core/auth/auth.types'
import {
  canReadIngredients,
  canReadPromotions,
  canReadPrices,
  canReadProducts,
  canReadRecipes,
  canWriteIngredients,
  canWriteProducts,
  canWritePromotions,
} from '@modules/catalog/services/catalogPermission.service'
import {
  canPreparePayroll as canPrepareFinancePayroll,
  canReadFinanceConfig,
  canReadPaymentRequests,
  canReadPayroll,
  canReadSuppliers,
  canWriteFinanceConfig,
} from '@modules/finance/services/financePermission.service'
import {
  canCreateGoodsReceipt,
  canCreatePurchaseOrder,
} from '@modules/procurement/services/procurementPermission.service'
import {
  canCreateStockAdjustments,
  canCreateStockCountSessions,
  canCreateWasteRecords,
  canReadStockBalances,
  canReadInventoryLedger,
} from '@modules/inventory/services/inventoryPermission.service'
import {
  canReadOutlets,
  canReadRegions,
  canWriteOutlets,
  canWriteRegions,
} from '@modules/org/services/orgPermission.service'
import { iamUiPolicy } from '@modules/iam/services/iamUiPolicy.service'
import {
  canReadInventoryReport,
  canReadPayrollReport,
  canReadRevenueReport,
  canViewExportJobs,
} from '@modules/reports/services/reportsUiPolicy.service'
import {
  canApproveAttendance,
  canRecordAttendance,
} from '@modules/workforce/services/workforcePermission.service'
import {
  canReadAttendanceSummary,
  canReadAssignments,
  canReadContracts,
  canReadEmployees,
  canPreparePayroll,
  canWriteAssignments,
  canWriteContracts,
  canWriteEmployees,
} from '@modules/hr/services/hrPermission.service'
import { canReadAudit } from '@modules/audit/services/auditUiPolicy.service'

export function resolveCatalogLandingPath(principal: FernPrincipal | null): string | null {
  if (canReadProducts(principal)) {
    return '/catalog/products'
  }
  if (canReadIngredients(principal)) {
    return '/catalog/ingredients'
  }
  if (canReadRecipes(principal)) {
    return '/catalog/recipes'
  }
  if (canReadPrices(principal)) {
    return '/catalog/pricing'
  }
  if (canReadPromotions(principal) || canWritePromotions(principal)) {
    return '/catalog/promotions'
  }
  if (canWriteProducts(principal)) {
    return '/catalog/products/new'
  }
  if (canWriteIngredients(principal)) {
    return '/catalog/ingredients/new'
  }
  return null
}

export function resolveIamLandingPath(principal: FernPrincipal | null): string | null {
  if (iamUiPolicy.canReadUsers(principal)) {
    return '/iam/users'
  }
  if (iamUiPolicy.canOpenAssignmentsPage(principal)) {
    return '/iam/assignments'
  }
  return null
}

export function resolveFinanceLandingPath(principal: FernPrincipal | null): string | null {
  if (canReadSuppliers(principal)) {
    return '/finance/suppliers'
  }
  if (canReadPaymentRequests(principal)) {
    return '/finance/payment-requests'
  }
  if (canReadPayroll(principal)) {
    return '/finance/payroll-approvals'
  }
  if (canPrepareFinancePayroll(principal)) {
    return '/finance/payroll-periods'
  }
  if (canReadFinanceConfig(principal) || canWriteFinanceConfig(principal)) {
    return '/finance/config'
  }
  return null
}

export function resolveHrLandingPath(principal: FernPrincipal | null): string | null {
  if (canReadEmployees(principal)) {
    return '/hr/employees'
  }
  if (canWriteEmployees(principal)) {
    return '/hr/employees/new'
  }
  if (canReadContracts(principal)) {
    return '/hr/contracts'
  }
  if (canWriteContracts(principal)) {
    return '/hr/contracts/new'
  }
  if (canReadAssignments(principal) || canWriteAssignments(principal)) {
    return '/hr/shift-scheduling'
  }
  if (canReadAttendanceSummary(principal)) {
    return '/hr/attendance-summary'
  }
  if (canReadPayroll(principal) || canPreparePayroll(principal)) {
    return '/hr/payroll-preparation'
  }
  return null
}

export function resolveProcurementLandingPath(principal: FernPrincipal | null): string | null {
  if (canCreatePurchaseOrder(principal)) {
    return '/procurement/purchase-orders/new'
  }
  if (canCreateGoodsReceipt(principal)) {
    return '/procurement/goods-receipts/new'
  }
  return null
}

export function resolveInventoryLandingPath(principal: FernPrincipal | null): string | null {
  if (canReadStockBalances(principal)) {
    return '/inventory/stock-balances'
  }
  if (canReadInventoryLedger(principal)) {
    return '/inventory/transactions'
  }
  if (canCreateStockAdjustments(principal)) {
    return '/inventory/stock-adjustments/new'
  }
  if (canCreateWasteRecords(principal)) {
    return '/inventory/waste-records/new'
  }
  if (canCreateStockCountSessions(principal)) {
    return '/inventory/stock-count-sessions/new'
  }
  return null
}

export function resolveOrgLandingPath(principal: FernPrincipal | null): string | null {
  if (canReadOutlets(principal)) {
    return '/org/outlets'
  }
  if (canReadRegions(principal)) {
    return '/org/regions'
  }
  if (canWriteOutlets(principal)) {
    return '/org/outlets/new'
  }
  if (canWriteRegions(principal)) {
    return '/org/regions/new'
  }
  return null
}

export function resolveWorkforceLandingPath(principal: FernPrincipal | null): string | null {
  if (canRecordAttendance(principal)) {
    return '/workforce/my-attendance'
  }
  if (canApproveAttendance(principal)) {
    return '/workforce/attendance-approvals'
  }
  return null
}

export function resolveAuditLandingPath(principal: FernPrincipal | null): string | null {
  if (canReadAudit(principal)) {
    return '/audit/events'
  }
  return null
}

export function resolveReportsLandingPath(principal: FernPrincipal | null): string | null {
  if (canReadRevenueReport(principal) || canReadInventoryReport(principal) || canReadPayrollReport(principal)) {
    return null
  }
  if (canViewExportJobs(principal)) {
    return '/reports/export-jobs'
  }
  return null
}
