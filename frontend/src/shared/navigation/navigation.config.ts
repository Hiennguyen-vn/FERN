import { hasAnyPermissions } from '@core/permissions/permission.checker'
import { permissionConstants } from '@core/permissions/permission.constants'
import { canOpenReportDashboard } from '@modules/reports/services/reportsUiPolicy.service'
import type { NavigationItem } from './navigation.types'

const catalogReadPermissions = [
  permissionConstants.catalog.productRead,
  permissionConstants.catalog.ingredientRead,
  permissionConstants.catalog.recipeRead,
  permissionConstants.catalog.priceRead,
]

const posNavigationPermissions = [
  permissionConstants.pos.sessionRead,
  permissionConstants.pos.orderRead,
  permissionConstants.pos.orderCreate,
]

const procurementNavigationPermissions = [
  permissionConstants.procurement.supplierRead,
  permissionConstants.procurement.purchaseOrderRead,
  permissionConstants.procurement.purchaseOrderCreate,
  permissionConstants.procurement.goodsReceiptRead,
  permissionConstants.procurement.goodsReceiptCreate,
]

const inventoryNavigationPermissions = [
  permissionConstants.inventory.balanceRead,
  permissionConstants.inventory.ledgerRead,
]

const workforceNavigationPermissions = [
  permissionConstants.hr.attendanceWrite,
  permissionConstants.hr.attendanceReview,
]

const financeNavigationPermissions = [
  permissionConstants.procurement.supplierRead,
  permissionConstants.procurement.invoiceRead,
  permissionConstants.procurement.invoiceReview,
  permissionConstants.procurement.invoiceApprove,
  permissionConstants.procurement.invoiceDispute,
  permissionConstants.procurement.paymentRead,
  permissionConstants.procurement.paymentRecord,
  permissionConstants.finance.payrollRead,
  permissionConstants.finance.payrollApprove,
  permissionConstants.finance.payrollPay,
]

const iamReadPermissions = [
  permissionConstants.iam.userRead,
  permissionConstants.iam.roleRead,
  permissionConstants.iam.permissionRead,
  permissionConstants.iam.permissionOverrideRead,
]

const auditReadPermissions = [permissionConstants.audit.read]

const hrReadPermissions = [
  permissionConstants.hr.employeeRead,
  permissionConstants.hr.contractRead,
  permissionConstants.hr.shiftRead,
  permissionConstants.hr.attendanceReview,
  permissionConstants.finance.payrollRead,
  permissionConstants.finance.payrollPrepare,
]

const regionalOpsPermissions = [
  permissionConstants.org.regionRead,
  permissionConstants.org.outletRead,
]

export const navigationConfig: NavigationItem[] = [
  { label: 'Home', to: '/home' },
  {
    label: 'POS',
    to: '/pos',
    visible: (principal) => hasAnyPermissions(principal, posNavigationPermissions),
  },
  {
    label: 'Catalog',
    to: '/catalog/products',
    visible: (principal) => hasAnyPermissions(principal, catalogReadPermissions),
  },
  {
    label: 'IAM',
    to: '/iam/assignments',
    visible: (principal) => hasAnyPermissions(principal, iamReadPermissions),
  },
  {
    label: 'Audit',
    to: '/audit/events',
    visible: (principal) => hasAnyPermissions(principal, auditReadPermissions),
  },
  {
    label: 'Regional Ops',
    to: '/regional-ops',
    visible: (principal) => hasAnyPermissions(principal, regionalOpsPermissions),
  },
  {
    label: 'HR',
    to: '/hr/employees',
    visible: (principal) => hasAnyPermissions(principal, hrReadPermissions),
  },
  {
    label: 'Finance',
    to: '/finance/payroll-approvals',
    visible: (principal) => hasAnyPermissions(principal, financeNavigationPermissions),
  },
  {
    label: 'Procurement',
    to: '/procurement',
    visible: (principal) => hasAnyPermissions(principal, procurementNavigationPermissions),
  },
  {
    label: 'Inventory',
    to: '/inventory/stock-balances',
    visible: (principal) => hasAnyPermissions(principal, inventoryNavigationPermissions),
  },
  {
    label: 'Workforce',
    to: '/workforce/my-attendance',
    visible: (principal) => hasAnyPermissions(principal, workforceNavigationPermissions),
  },
  {
    label: 'Reports',
    to: '/reports',
    visible: (principal) => canOpenReportDashboard(principal),
  },
]
