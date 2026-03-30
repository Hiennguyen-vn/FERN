import type { CreateExportPayload } from '../model/reportExport.types'
import type { ExportJobFormValues } from './exportJob.schema'

function toOptionalNumber(value?: string): number | undefined {
  if (!value) {
    return undefined
  }

  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : undefined
}

export function mapExportFormToPayload(values: ExportJobFormValues): CreateExportPayload {
  return {
    dataset: values.dataset as CreateExportPayload['dataset'],
    format: values.format as CreateExportPayload['format'],
    fromDate: values.fromDate || undefined,
    toDate: values.toDate || undefined,
    regionId: toOptionalNumber(values.regionId),
    outletId: toOptionalNumber(values.outletId),
    payrollRunId: toOptionalNumber(values.payrollRunId),
    limit: toOptionalNumber(values.limit),
  }
}
