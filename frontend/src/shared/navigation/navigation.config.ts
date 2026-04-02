import { hasAnyPermissions } from '@core/permissions/permission.checker'
import { permissionConstants } from '@core/permissions/permission.constants'
import { canOpenReportDashboard } from '@modules/reports/services/reportsUiPolicy.service'
import type { NavigationItem } from './navigation.types'

const catalogReadPermissions = [
  permissionConstants.catalog.productRead,
  permissionConstants.catalog.productWrite,
  permissionConstants.catalog.ingredientRead,
  permissionConstants.catalog.ingredientWrite,
  permissionConstants.catalog.recipeRead,
  permissionConstants.catalog.recipeWrite,
  permissionConstants.catalog.priceRead,
  permissionConstants.catalog.priceWrite,
  permissionConstants.catalog.promotionRead,
  permissionConstants.catalog.promotionWrite,
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
  permissionConstants.inventory.adjustmentWrite,
  permissionConstants.inventory.wasteWrite,
  permissionConstants.inventory.stockCountWrite,
  permissionConstants.inventory.stockCountPost,
]

const workforceNavigationPermissions = [
  permissionConstants.hr.attendanceWrite,
  permissionConstants.hr.attendanceReview,
]

const financeNavigationPermissions = [
  permissionConstants.procurement.supplierRead,
  permissionConstants.procurement.supplierWrite,
  permissionConstants.procurement.invoiceRead,
  permissionConstants.procurement.invoiceReview,
  permissionConstants.procurement.invoiceApprove,
  permissionConstants.procurement.invoiceDispute,
  permissionConstants.procurement.paymentRead,
  permissionConstants.procurement.paymentRecord,
  permissionConstants.finance.payrollRead,
  permissionConstants.finance.payrollPrepare,
  permissionConstants.finance.payrollApprove,
  permissionConstants.finance.payrollPay,
  permissionConstants.finance.configRead,
  permissionConstants.finance.configWrite,
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
  permissionConstants.hr.employeeWrite,
  permissionConstants.hr.contractRead,
  permissionConstants.hr.contractWrite,
  permissionConstants.hr.shiftRead,
  permissionConstants.hr.shiftWrite,
  permissionConstants.hr.payrollPrepare,
  permissionConstants.hr.attendanceReview,
  permissionConstants.finance.payrollRead,
  permissionConstants.finance.payrollPrepare,
]

const regionalOpsPermissions = [
  permissionConstants.org.regionRead,
  permissionConstants.org.outletRead,
  permissionConstants.org.regionWrite,
  permissionConstants.org.outletWrite,
]

export const navigationConfig: NavigationItem[] = [
  { icon: 'dashboard', label: 'Home', to: '/home' },
  {
    icon: 'point_of_sale',
    label: 'POS',
    to: '/pos',
    visible: (principal) => hasAnyPermissions(principal, posNavigationPermissions),
  },
  {
    icon: 'restaurant_menu',
    label: 'Catalog',
    to: '/catalog',
    visible: (principal) => hasAnyPermissions(principal, catalogReadPermissions),
  },
  {
    icon: 'group',
    label: 'IAM',
    to: '/iam',
    visible: (principal) => hasAnyPermissions(principal, iamReadPermissions),
  },
  {
    icon: 'history',
    label: 'Audit',
    to: '/audit',
    visible: (principal) => hasAnyPermissions(principal, auditReadPermissions),
  },
  {
    icon: 'account_tree',
    label: 'Org',
    to: '/org',
    visible: (principal) => hasAnyPermissions(principal, regionalOpsPermissions),
  },
  {
    icon: 'storefront',
    label: 'Regional Ops',
    to: '/regional-ops',
    visible: (principal) => hasAnyPermissions(principal, regionalOpsPermissions),
  },
  {
    icon: 'badge',
    label: 'HR',
    to: '/hr',
    visible: (principal) => hasAnyPermissions(principal, hrReadPermissions),
  },
  {
    icon: 'payments',
    label: 'Finance',
    to: '/finance',
    visible: (principal) => hasAnyPermissions(principal, financeNavigationPermissions),
  },
  {
    icon: 'shopping_cart',
    label: 'Procurement',
    to: '/procurement',
    visible: (principal) => hasAnyPermissions(principal, procurementNavigationPermissions),
  },
  {
    icon: 'inventory',
    label: 'Inventory',
    to: '/inventory',
    visible: (principal) => hasAnyPermissions(principal, inventoryNavigationPermissions),
  },
  {
    icon: 'schedule',
    label: 'Workforce',
    to: '/workforce',
    visible: (principal) => hasAnyPermissions(principal, workforceNavigationPermissions),
  },
  {
    icon: 'leaderboard',
    label: 'Reports',
    to: '/reports',
    visible: (principal) => canOpenReportDashboard(principal),
  },
]
