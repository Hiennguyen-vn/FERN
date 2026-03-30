import type { ExportDataset, ExportFormat } from './reportExport.types'

export const exportDatasetOptions: Array<{ label: string; value: ExportDataset }> = [
  { label: 'Sales Fact', value: 'SALES_FACT' },
  { label: 'Payment Fact', value: 'PAYMENT_FACT' },
  { label: 'Inventory Movement Fact', value: 'INVENTORY_MOVEMENT_FACT' },
  { label: 'Procurement Fact', value: 'PROCUREMENT_FACT' },
  { label: 'Attendance Fact', value: 'ATTENDANCE_FACT' },
  { label: 'Payroll Fact', value: 'PAYROLL_FACT' },
  { label: 'Expense Fact', value: 'EXPENSE_FACT' },
  { label: 'Region Daily Summary', value: 'REGION_DAILY_SUMMARY' },
  { label: 'Company Daily Summary', value: 'COMPANY_DAILY_SUMMARY' },
  { label: 'Payroll Summary', value: 'PAYROLL_SUMMARY' },
  { label: 'Payroll Run', value: 'PAYROLL_RUN' },
]

export const exportFormatOptions: Array<{ label: string; value: ExportFormat }> = [
  { label: 'CSV', value: 'CSV' },
]
