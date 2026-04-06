import type { PayrollPeriod, PayrollRun, PayrollEmployee, PayrollAuditEntry, FinanceConfigSection, ExportJob } from '@/types/finance';

export const mockPayrollPeriods: PayrollPeriod[] = [
  { id: 'pp-001', regionId: 'region-central', regionName: 'Central Region', startDate: '2025-01-01', endDate: '2025-01-15', status: 'closed', runCount: 1 },
  { id: 'pp-002', regionId: 'region-central', regionName: 'Central Region', startDate: '2025-01-16', endDate: '2025-01-31', status: 'open', runCount: 1 },
  { id: 'pp-003', regionId: 'region-north', regionName: 'North Region', startDate: '2025-01-01', endDate: '2025-01-15', status: 'closed', runCount: 1 },
  { id: 'pp-004', regionId: 'region-north', regionName: 'North Region', startDate: '2025-01-16', endDate: '2025-01-31', status: 'open', runCount: 0 },
  { id: 'pp-005', regionId: 'region-south', regionName: 'South Region', startDate: '2025-01-01', endDate: '2025-01-15', status: 'locked', runCount: 1 },
  { id: 'pp-006', regionId: 'region-south', regionName: 'South Region', startDate: '2025-01-16', endDate: '2025-01-31', status: 'open', runCount: 0 },
];

export const mockPayrollRuns: PayrollRun[] = [
  { id: 'PR-2025-001', periodId: 'pp-001', regionName: 'Central Region', periodLabel: 'Jan 1–15', employeeCount: 32, grossPay: 48600, deductions: 9720, netPay: 38880, status: 'paid', preparedBy: 'Elena Vasquez', approvedBy: 'Sarah Chen', createdAt: '2025-01-14' },
  { id: 'PR-2025-002', periodId: 'pp-002', regionName: 'Central Region', periodLabel: 'Jan 16–31', employeeCount: 34, grossPay: 51200, deductions: 10240, netPay: 40960, status: 'submitted', preparedBy: 'Elena Vasquez', approvedBy: null, createdAt: '2025-01-28' },
  { id: 'PR-2025-003', periodId: 'pp-003', regionName: 'North Region', periodLabel: 'Jan 1–15', employeeCount: 18, grossPay: 27000, deductions: 5400, netPay: 21600, status: 'approved', preparedBy: 'Elena Vasquez', approvedBy: 'Sarah Chen', createdAt: '2025-01-14' },
  { id: 'PR-2025-004', periodId: 'pp-005', regionName: 'South Region', periodLabel: 'Jan 1–15', employeeCount: 8, grossPay: 12000, deductions: 2400, netPay: 9600, status: 'draft', preparedBy: 'Elena Vasquez', approvedBy: null, createdAt: '2025-01-20' },
];

export const mockPayrollEmployees: PayrollEmployee[] = [
  { id: 'emp-001', name: 'Aisha Patel', outlet: 'Downtown Flagship', role: 'Outlet Manager', hoursWorked: 176, basePay: 3200, overtime: 0, deductions: 640, netPay: 2560 },
  { id: 'emp-002', name: 'James Okonkwo', outlet: 'Riverside Branch', role: 'POS Cashier', hoursWorked: 168, basePay: 1800, overtime: 120, deductions: 384, netPay: 1536 },
  { id: 'emp-003', name: 'Lina Torres', outlet: 'Downtown Flagship', role: 'Kitchen Staff', hoursWorked: 180, basePay: 1600, overtime: 200, deductions: 360, netPay: 1440 },
  { id: 'emp-004', name: 'Chen Wei', outlet: 'Mall Kiosk A', role: 'POS Cashier', hoursWorked: 160, basePay: 1700, overtime: 0, deductions: 340, netPay: 1360 },
  { id: 'emp-005', name: 'Priya Sharma', outlet: 'Downtown Flagship', role: 'Barista', hoursWorked: 172, basePay: 1650, overtime: 80, deductions: 346, netPay: 1384 },
  { id: 'emp-006', name: 'Omar Hassan', outlet: 'Riverside Branch', role: 'Kitchen Staff', hoursWorked: 176, basePay: 1600, overtime: 150, deductions: 350, netPay: 1400 },
];

export const mockPayrollAudit: PayrollAuditEntry[] = [
  { action: 'Created', actor: 'Elena Vasquez', timestamp: '2025-01-14T09:00:00Z', detail: 'Payroll run PR-2025-001 created for Central Region Jan 1–15' },
  { action: 'Submitted', actor: 'Elena Vasquez', timestamp: '2025-01-14T14:30:00Z', detail: 'Submitted for approval with 32 employees, net pay $38,880' },
  { action: 'Approved', actor: 'Sarah Chen', timestamp: '2025-01-15T09:15:00Z', detail: 'Approved — all line items verified' },
  { action: 'Marked Paid', actor: 'Elena Vasquez', timestamp: '2025-01-16T10:00:00Z', detail: 'Payment batch processed via bank transfer' },
];

export const mockFinanceConfig: FinanceConfigSection[] = [
  {
    id: 'numbering',
    label: 'Document Numbering',
    description: 'Auto-numbering rules for finance documents',
    settings: [
      { key: 'payroll-prefix', label: 'Payroll Run Prefix', value: 'PR-{YEAR}-', type: 'text' },
      { key: 'invoice-prefix', label: 'Invoice Prefix', value: 'INV-{YEAR}-', type: 'text' },
      { key: 'payment-prefix', label: 'Payment Prefix', value: 'PAY-{YEAR}-', type: 'text' },
      { key: 'next-sequence', label: 'Next Sequence Number', value: '005', type: 'number' },
    ],
  },
  {
    id: 'payroll',
    label: 'Payroll Settings',
    description: 'Configuration for payroll processing',
    settings: [
      { key: 'period-type', label: 'Pay Period Type', value: 'Bi-monthly', type: 'select', options: ['Weekly', 'Bi-monthly', 'Monthly'] },
      { key: 'overtime-multiplier', label: 'Overtime Multiplier', value: '1.5', type: 'number' },
      { key: 'tax-rate', label: 'Default Tax Rate (%)', value: '20', type: 'number' },
      { key: 'auto-deductions', label: 'Auto-calculate Deductions', value: 'true', type: 'boolean' },
    ],
  },
  {
    id: 'approvals',
    label: 'Approval Workflows',
    description: 'Thresholds and routing for financial approvals',
    settings: [
      { key: 'payroll-approval', label: 'Payroll Requires Approval', value: 'true', type: 'boolean' },
      { key: 'payroll-threshold', label: 'Auto-approve Below ($)', value: '5000', type: 'number' },
      { key: 'po-approval-threshold', label: 'PO Approval Threshold ($)', value: '1000', type: 'number' },
    ],
  },
  {
    id: 'export',
    label: 'Export Settings',
    description: 'Default export formats and retention',
    settings: [
      { key: 'default-format', label: 'Default Export Format', value: 'CSV', type: 'select', options: ['CSV', 'XLSX', 'PDF'] },
      { key: 'retention-days', label: 'Export File Retention (days)', value: '90', type: 'number' },
    ],
  },
];

export const mockExportJobs: ExportJob[] = [
  { id: 'exp-001', module: 'Finance', label: 'Payroll Run PR-2025-001', requester: 'Elena Vasquez', scope: 'Central Region', requestedAt: '2025-01-16T10:30:00Z', completedAt: '2025-01-16T10:31:12Z', status: 'completed', fileSize: '24 KB' },
  { id: 'exp-002', module: 'Reports', label: 'Revenue Dashboard — Jan 2025', requester: 'Sarah Chen', scope: 'System-wide', requestedAt: '2025-01-15T15:00:00Z', completedAt: '2025-01-15T15:02:45Z', status: 'completed', fileSize: '156 KB' },
  { id: 'exp-003', module: 'Inventory', label: 'Stock Balance — All Outlets', requester: 'Marcus Rivera', scope: 'Central Region', requestedAt: '2025-01-15T14:00:00Z', completedAt: null, status: 'processing', fileSize: null },
  { id: 'exp-004', module: 'Audit', label: 'Security Events — Dec 2024', requester: 'Sarah Chen', scope: 'System-wide', requestedAt: '2025-01-14T09:00:00Z', completedAt: '2025-01-14T09:05:30Z', status: 'completed', fileSize: '89 KB' },
  { id: 'exp-005', module: 'POS', label: 'Sales Summary — Week 2', requester: 'Aisha Patel', scope: 'Downtown Flagship', requestedAt: '2025-01-13T16:00:00Z', completedAt: null, status: 'failed', fileSize: null },
  { id: 'exp-006', module: 'Procurement', label: 'Supplier Ledger Export', requester: 'David Kim', scope: 'North Region', requestedAt: '2025-01-15T11:00:00Z', completedAt: null, status: 'queued', fileSize: null },
];
