import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { LazyRouteBoundary } from '@app/router/LazyRouteBoundary'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import {
  PaymentRequestsPage,
  PayrollApprovalDetailPage,
  PayrollApprovalPage,
  PayrollPaidPage,
  SupplierDetailPage,
  SuppliersPage,
} from '../routes/financeRoutes.bundle'

const mocks = vi.hoisted(() => ({
  useFinanceSupplier: vi.fn(),
  useFinanceSuppliers: vi.fn(),
  usePaymentRequest: vi.fn(),
  usePayrollApprovalQueue: vi.fn(),
  usePayrollRun: vi.fn(),
  useRecentPaymentRequests: vi.fn(),
  useApprovePayrollRun: vi.fn(),
  useRejectPayrollRun: vi.fn(),
  useCancelPayrollRun: vi.fn(),
  useMarkPayrollPaid: vi.fn(),
}))

vi.mock('../hooks/useFinance', () => ({
  useFinanceSupplier: mocks.useFinanceSupplier,
  useFinanceSuppliers: mocks.useFinanceSuppliers,
  usePaymentRequest: mocks.usePaymentRequest,
  usePayrollApprovalQueue: mocks.usePayrollApprovalQueue,
  usePayrollRun: mocks.usePayrollRun,
  useRecentPaymentRequests: mocks.useRecentPaymentRequests,
  useApprovePayrollRun: mocks.useApprovePayrollRun,
  useRejectPayrollRun: mocks.useRejectPayrollRun,
  useCancelPayrollRun: mocks.useCancelPayrollRun,
  useMarkPayrollPaid: mocks.useMarkPayrollPaid,
}))

function FinanceRoutesHarness() {
  return (
    <Routes>
      <Route path="/finance" element={<LazyRouteBoundary moduleName="Finance" label="Loading finance workspace" />}>
        <Route path="suppliers" element={<SuppliersPage />} />
        <Route path="suppliers/:supplierId" element={<SupplierDetailPage />} />
        <Route path="payment-requests" element={<PaymentRequestsPage />} />
        <Route path="payroll-approvals" element={<PayrollApprovalPage />} />
        <Route path="payroll-approvals/:runId" element={<PayrollApprovalDetailPage />} />
        <Route path="payroll-paid/:runId" element={<PayrollPaidPage />} />
      </Route>
    </Routes>
  )
}

describe('Finance route group', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [
          permissionConstants.procurement.supplierRead,
          permissionConstants.procurement.invoiceRead,
          permissionConstants.finance.payrollRead,
          permissionConstants.finance.payrollApprove,
          permissionConstants.finance.payrollPay,
          permissionConstants.finance.payrollDetailRead,
        ],
      },
    })

    mocks.useFinanceSuppliers.mockReturnValue({
      data: [
        {
          id: 12,
          supplierCode: 'SUP-012',
          name: 'Lotus Foods',
          taxCode: 'TAX-12',
          email: 'lotus@fern.local',
          phone: '0901',
          address: '1 Nguyen Hue',
          defaultRegionId: 1,
          status: 'ACTIVE',
          approvedAt: '2026-03-29T09:00:00Z',
        },
      ],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useFinanceSupplier.mockReturnValue({
      data: {
        id: 12,
        supplierCode: 'SUP-012',
        name: 'Lotus Foods',
        taxCode: 'TAX-12',
        email: 'lotus@fern.local',
        phone: '0901',
        address: '1 Nguyen Hue',
        defaultRegionId: 1,
        status: 'ACTIVE',
        approvedAt: '2026-03-29T09:00:00Z',
      },
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useRecentPaymentRequests.mockReturnValue({
      items: [],
      clear: vi.fn(),
      refresh: vi.fn(),
      save: vi.fn(),
    })
    mocks.usePaymentRequest.mockReturnValue({
      data: undefined,
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.usePayrollApprovalQueue.mockReturnValue({
      data: [],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.usePayrollRun.mockReturnValue({
      data: {
        id: 55,
        payrollPeriodId: 11,
        runCode: 'RUN-000055',
        runDate: '2026-03-30',
        status: 'SUBMITTED',
        totalAmount: 45000000,
        paymentRef: null,
        note: 'March payroll',
        submittedAt: '2026-03-30T08:00:00Z',
        approvedAt: null,
        paidAt: null,
        employees: [],
      },
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useApprovePayrollRun.mockReturnValue({ mutateAsync: vi.fn(), isPending: false })
    mocks.useRejectPayrollRun.mockReturnValue({ mutateAsync: vi.fn(), isPending: false })
    mocks.useCancelPayrollRun.mockReturnValue({ mutateAsync: vi.fn(), isPending: false })
    mocks.useMarkPayrollPaid.mockReturnValue({ mutateAsync: vi.fn(), isPending: false })
  })

  it.each([
    ['/finance/suppliers', 'Nhà cung cấp'],
    ['/finance/suppliers/12', 'Chi tiết nhà cung cấp'],
    ['/finance/payment-requests', 'Payment Requests'],
    ['/finance/payroll-approvals', 'Payroll Approvals'],
    ['/finance/payroll-approvals/55', 'Payroll Approval Detail'],
    ['/finance/payroll-paid/55', 'Payroll Paid'],
  ])('resolves %s', async (route, heading) => {
    renderWithProviders(<FinanceRoutesHarness />, { route })

    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument()
  })
})
