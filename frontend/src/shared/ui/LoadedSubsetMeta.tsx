/**
 * LoadedSubsetMeta
 *
 * Renders a standardised meta-grid row for pages that load a flat, limited
 * batch from the backend and apply client-side filtering on top.
 *
 * Backend contract context:
 *   Many catalog/list endpoints return a single page of up to `maxPerBatch`
 *   records.  Server-side search is not available on these endpoints.
 *   Client-side filter/search therefore operates on the loaded subset only.
 *   This component makes that limitation explicit to the user.
 *
 * Usage:
 *   <LoadedSubsetMeta
 *     loadedCount={products.length}
 *     filteredCount={filteredRows.length}
 *     entityLabel="sản phẩm"
 *   />
 */
interface LoadedSubsetMetaProps {
  /** Number of records returned from the backend */
  loadedCount: number
  /** Number of records visible after client-side filtering */
  filteredCount: number
  /** Singular or plural entity label in Vietnamese, e.g. "sản phẩm", "nguyên liệu" */
  entityLabel: string
  /** Backend default limit — shown in the batch size note (default 200) */
  maxPerBatch?: number
}

export function LoadedSubsetMeta({
  loadedCount,
  filteredCount,
  entityLabel,
  maxPerBatch = 200,
}: LoadedSubsetMetaProps) {
  return (
    <div className="meta-grid">
      <span>
        Đã tải: {loadedCount} {entityLabel} (tối đa {maxPerBatch}/lần)
      </span>
      <span>Kết quả sau lọc: {filteredCount}</span>
      <span>Tìm kiếm áp dụng trên dữ liệu đã tải</span>
    </div>
  )
}
