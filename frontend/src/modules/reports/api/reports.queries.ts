export const reportQueryKeys = {
  dashboard: ['reports', 'dashboard'] as const,
  exportJob: (jobId: number) => ['reports', 'exports', jobId] as const,
  exportPreview: (jobId: number) => ['reports', 'exports', jobId, 'preview'] as const,
  exportJobs: (jobIds: number[]) => ['reports', 'exports', 'recent', ...jobIds] as const,
  inventoryBalances: (filters: Record<string, unknown>) => ['reports', 'inventory', 'balances', filters] as const,
  inventoryTransactions: (filters: Record<string, unknown>) =>
    ['reports', 'inventory', 'transactions', filters] as const,
  payrollRunReport: (runId: number) => ['reports', 'payroll', 'run', runId] as const,
  payrollRuns: (regionId?: number) => ['reports', 'payroll', 'runs', regionId ?? 'all'] as const,
  payrollSummary: (filters: Record<string, unknown>) => ['reports', 'payroll', 'summary', filters] as const,
}
