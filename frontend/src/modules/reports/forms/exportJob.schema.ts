import { z } from 'zod'

export const exportJobSchema = z.object({
  dataset: z.string().min(1, 'Dataset is required'),
  format: z.string().min(1, 'Format is required'),
  fromDate: z.string().optional(),
  toDate: z.string().optional(),
  regionId: z.string().optional(),
  outletId: z.string().optional(),
  payrollRunId: z.string().optional(),
  limit: z.string().optional(),
})

export type ExportJobFormValues = z.infer<typeof exportJobSchema>
