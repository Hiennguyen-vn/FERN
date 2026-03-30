import { Card, DataTable } from '@design-system/index'

interface ExportPreviewPanelProps {
  rows: Array<Record<string, unknown>>
}

export function ExportPreviewPanel({ rows }: ExportPreviewPanelProps) {
  if (rows.length === 0) {
    return (
      <Card title="Preview">
        <p className="muted-text">No preview rows available for this export job yet.</p>
      </Card>
    )
  }

  const columnKeys = Object.keys(rows[0])

  return (
    <Card title="Preview">
      <DataTable
        columns={columnKeys.map((key) => ({
          key,
          header: key,
          render: (row: Record<string, unknown>) => String(row[key] ?? ''),
        }))}
        rows={rows}
      />
    </Card>
  )
}
