import { useState } from 'react'
import type { Product, ProductStatus, ProductUpsertRequest } from '../model/catalog.types'

const inputStyle: React.CSSProperties = {
  width: '100%',
  padding: '0.625rem 0.875rem',
  background: 'var(--bg-elevated)',
  border: '1px solid var(--border-default)',
  borderRadius: 'var(--radius-md)',
  color: 'var(--text-primary)',
  fontSize: 'var(--text-sm)',
  outline: 'none',
}

const labelStyle: React.CSSProperties = {
  display: 'block',
  fontSize: 'var(--text-xs)',
  fontWeight: 'var(--font-medium)',
  color: 'var(--text-secondary)',
  marginBottom: '0.375rem',
  textTransform: 'uppercase',
  letterSpacing: '0.05em',
}

interface ProductFormProps {
  initial?: Partial<Product>
  categories: { code: string; name: string }[]
  onSubmit: (data: ProductUpsertRequest) => Promise<void>
  onCancel: () => void
  submitting: boolean
}

export function ProductForm({ initial, categories, onSubmit, onCancel, submitting }: ProductFormProps) {
  const [form, setForm] = useState<ProductUpsertRequest>({
    code: initial?.code ?? '',
    name: initial?.name ?? '',
    categoryCode: initial?.categoryCode ?? null,
    status: initial?.status ?? 'ACTIVE',
    imageUrl: initial?.imageUrl ?? null,
    description: initial?.description ?? null,
  })

  return (
    <form
      onSubmit={async (e) => {
        e.preventDefault()
        await onSubmit(form)
      }}
      style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}
    >
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
        <div>
          <label style={labelStyle}>Mã sản phẩm *</label>
          <input
            required
            style={inputStyle}
            value={form.code}
            onChange={(e) => setForm((f) => ({ ...f, code: e.target.value.toUpperCase() }))}
            placeholder="PROD-001"
            disabled={!!initial?.id}
          />
        </div>
        <div>
          <label style={labelStyle}>Trạng thái *</label>
          <select
            required
            style={{ ...inputStyle, cursor: 'pointer' }}
            value={form.status}
            onChange={(e) => setForm((f) => ({ ...f, status: e.target.value as ProductStatus }))}
          >
            <option value="ACTIVE">Đang bán</option>
            <option value="INACTIVE">Ngừng bán</option>
            <option value="DISCONTINUED">Ngừng kinh doanh</option>
          </select>
        </div>
      </div>

      <div>
        <label style={labelStyle}>Tên sản phẩm *</label>
        <input
          required
          style={inputStyle}
          value={form.name}
          onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
          placeholder="Cà phê sữa đá"
          maxLength={150}
        />
      </div>

      <div>
        <label style={labelStyle}>Danh mục</label>
        <select
          style={{ ...inputStyle, cursor: 'pointer' }}
          value={form.categoryCode ?? ''}
          onChange={(e) => setForm((f) => ({ ...f, categoryCode: e.target.value || null }))}
        >
          <option value="">— Không có danh mục —</option>
          {categories.map((c) => (
            <option key={c.code} value={c.code}>{c.name}</option>
          ))}
        </select>
      </div>

      <div>
        <label style={labelStyle}>Mô tả</label>
        <textarea
          rows={3}
          style={{ ...inputStyle, resize: 'vertical', minHeight: 80 }}
          value={form.description ?? ''}
          onChange={(e) => setForm((f) => ({ ...f, description: e.target.value || null }))}
          placeholder="Mô tả ngắn về sản phẩm..."
        />
      </div>

      <div style={{ display: 'flex', gap: '0.75rem', justifyContent: 'flex-end', paddingTop: '0.5rem' }}>
        <button
          type="button"
          onClick={onCancel}
          style={{ padding: '0.5rem 1.25rem', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-default)', background: 'transparent', color: 'var(--text-secondary)', fontSize: 'var(--text-sm)', cursor: 'pointer' }}
        >
          Hủy
        </button>
        <button
          type="submit"
          disabled={submitting}
          style={{ padding: '0.5rem 1.5rem', borderRadius: 'var(--radius-md)', border: 'none', background: 'var(--brand)', color: '#000', fontSize: 'var(--text-sm)', fontWeight: 'var(--font-semibold)', cursor: submitting ? 'not-allowed' : 'pointer', opacity: submitting ? 0.7 : 1 }}
        >
          {submitting ? 'Đang lưu...' : 'Lưu sản phẩm'}
        </button>
      </div>
    </form>
  )
}
