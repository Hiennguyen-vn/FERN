import { useEffect, useMemo, useState } from 'react'
import { Button, FormActions, FormSection, Input, Select } from '@design-system/index'
import type { SelectOption } from '@design-system/index'
import { exportJobSchema, type ExportJobFormValues } from '../forms/exportJob.schema'
import { mapExportFormToPayload } from '../forms/reportFilter.mapper'
import { exportDatasetOptions, exportFormatOptions } from '../model/reports.enums'
import type { ExportDataset } from '../model/reportExport.types'
import { requiresDateRange, requiresPayrollRunId, requiresRegion } from '../services/reportFilter.service'

const LARGE_EXPORT_LIMIT = 500
const LARGE_DATE_RANGE_DAYS = 90

interface ReportFilterPanelProps {
  allowedDatasets: ExportDataset[]
  isSubmitting?: boolean
  onSubmit: (values: ReturnType<typeof mapExportFormToPayload>) => Promise<void> | void
}

const initialValues: ExportJobFormValues = {
  dataset: 'SALES_FACT',
  format: 'CSV',
  fromDate: '',
  toDate: '',
  regionId: '',
  outletId: '',
  payrollRunId: '',
  limit: '20',
}

export function ReportFilterPanel({ allowedDatasets, isSubmitting = false, onSubmit }: ReportFilterPanelProps) {
  const [values, setValues] = useState<ExportJobFormValues>(initialValues)
  const [errors, setErrors] = useState<Record<string, string>>({})

  const dataset = values.dataset as typeof exportDatasetOptions[number]['value']
  const datasetOptions = useMemo<Array<SelectOption>>(
    () =>
      exportDatasetOptions
        .filter((option) => allowedDatasets.includes(option.value))
        .map((option) => ({ label: option.label, value: option.value })),
    [allowedDatasets],
  )
  const formatOptions = useMemo<Array<SelectOption>>(
    () => exportFormatOptions.map((option) => ({ label: option.label, value: option.value })),
    [],
  )

  useEffect(() => {
    if (datasetOptions.length === 0) {
      return
    }

    if (datasetOptions.some((option) => option.value === values.dataset)) {
      return
    }

    setValues((current) => ({
      ...current,
      dataset: String(datasetOptions[0]?.value ?? current.dataset),
    }))
  }, [datasetOptions, values.dataset])

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const parsed = exportJobSchema.safeParse(values)
    if (!parsed.success) {
      const nextErrors = Object.fromEntries(
        parsed.error.issues.map((issue) => [issue.path.join('.'), issue.message]),
      )
      setErrors(nextErrors)
      return
    }

    setErrors({})
    await onSubmit(mapExportFormToPayload(parsed.data))
  }

  const limitNum = Number(values.limit)
  const isLargeLimit = !Number.isNaN(limitNum) && limitNum > LARGE_EXPORT_LIMIT

  const isLargeDateRange = (() => {
    if (!values.fromDate || !values.toDate) return false
    const from = new Date(values.fromDate)
    const to = new Date(values.toDate)
    const diffDays = (to.getTime() - from.getTime()) / (1000 * 60 * 60 * 24)
    return diffDays > LARGE_DATE_RANGE_DAYS
  })()

  const sizeWarning =
    isLargeLimit
      ? `Preview limit đang đặt ở ${String(limitNum)} rows — export job lớn có thể mất nhiều thời gian xử lý và ảnh hưởng hiệu năng server.`
      : isLargeDateRange
      ? `Khoảng thời gian vượt quá ${String(LARGE_DATE_RANGE_DAYS)} ngày — export job có thể sinh ra file lớn. Cân nhắc thu hẹp khoảng lọc.`
      : null

  return (
    <form onSubmit={handleSubmit}>
      <FormSection
        description="Create a report export job against the existing backend report-service contract."
        title="Create export job"
      >
        {sizeWarning ? (
          <div className="inline-banner inline-banner-warning" role="alert">
            <span>⚠ {sizeWarning}</span>
          </div>
        ) : null}
        <div className="field-grid">
          <Select
            error={errors.dataset}
            label="Dataset"
            onChange={(event) => setValues((current) => ({ ...current, dataset: event.target.value }))}
            options={datasetOptions}
            value={values.dataset}
          />
          <Select
            error={errors.format}
            label="Format"
            onChange={(event) => setValues((current) => ({ ...current, format: event.target.value }))}
            options={formatOptions}
            value={values.format}
          />
          {requiresRegion(dataset) ? (
            <Input
              error={errors.regionId}
              label="Region ID"
              onChange={(event) => setValues((current) => ({ ...current, regionId: event.target.value }))}
              placeholder="11"
              value={values.regionId}
            />
          ) : null}
          <Input
            error={errors.outletId}
            label="Outlet ID"
            onChange={(event) => setValues((current) => ({ ...current, outletId: event.target.value }))}
            placeholder="22"
            value={values.outletId}
          />
          {requiresDateRange(dataset) ? (
            <>
              <Input
                error={errors.fromDate}
                label="From date"
                onChange={(event) => setValues((current) => ({ ...current, fromDate: event.target.value }))}
                type="date"
                value={values.fromDate}
              />
              <Input
                error={errors.toDate}
                label="To date"
                onChange={(event) => setValues((current) => ({ ...current, toDate: event.target.value }))}
                type="date"
                value={values.toDate}
              />
            </>
          ) : null}
          {requiresPayrollRunId(dataset) ? (
            <Input
              error={errors.payrollRunId}
              label="Payroll run ID"
              onChange={(event) => setValues((current) => ({ ...current, payrollRunId: event.target.value }))}
              placeholder="1001"
              value={values.payrollRunId}
            />
          ) : null}
          <Input
            error={errors.limit}
            label={`Preview limit${isLargeLimit ? ' ⚠' : ''}`}
            onChange={(event) => setValues((current) => ({ ...current, limit: event.target.value }))}
            placeholder="20"
            value={values.limit}
          />
        </div>
        <FormActions
          primaryAction={
            <Button loading={isSubmitting} type="submit">
              Queue export
            </Button>
          }
        />
      </FormSection>
    </form>
  )
}
