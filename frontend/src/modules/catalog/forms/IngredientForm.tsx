import { useState } from 'react'
import type { Ingredient, IngredientStatus, IngredientUpsertRequest, UnitOfMeasure, Category } from '../model/catalog.types'

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

interface IngredientFormProps {
  initial?: Partial<Ingredient>
  categories: Category[]
  uoms: UnitOfMeasure[]
  onSubmit: (data: IngredientUpsertRequest) => Promise<void>
  onCancel: () => void
  submitting: boolean
}

export function IngredientForm({ initial, categories, uoms, onSubmit, onCancel, submitting }: IngredientFormProps) {
  const [form, setForm] = useState<IngredientUpsertRequest>({
    code: initial?.code ?? '',
    name: initial?.name ?? '',
    categoryCode: initial?.categoryCode ?? null,
    baseUomCode: initial?.baseUomCode ?? (uoms[0]?.code ?? ''),
    minStockLevel: initial?.minStockLevel ?? null,
    maxStockLevel: initial?.maxStockLevel ?? null,
    status: initial?.status ?? 'ACTIVE',
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
          <label style={labelStyle}>Mã *</label>
          <input
            required
            style={inputStyle}
            value={form.code}
            onChange={(e) => setForm((f) => ({ ...f, code: e.target.value.toUpperCase() }))}
            placeholder="ING-001"
            disabled={!!initial?.id}
          />
        </div>
        <div>
          <label style={labelStyle}>ĐVT cơ bản *</label>
          <select
            required
            style={{ ...inputStyle, cursor: 'pointer' }}
            value={form.baseUomCode}
            onChange={(e) => setForm((f) => ({ ...f, baseUomCode: e.target.value }))}
          >
            {uoms.map((u) => (
              <option key={u.code} value={u.code}>{u.name} ({u.code})</option>
            ))}
          </select>
        </div>
      </div>

      <div>
        <label style={labelStyle}>Tên nguyên liệu *</label>
        <input
          required
          style={inputStyle}
          value={form.name}
          onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
          placeholder="Cà phê robusta"
        />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
        <div>
          <label style={labelStyle}>Danh mục</label>
          <select
            style={{ ...inputStyle, cursor: 'pointer' }}
            value={form.categoryCode ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, categoryCode: e.target.value || null }))}
          >
            <option value="">— Không có —</option>
            {categories.map((c) => (
              <option key={c.code} value={c.code}>{c.name}</option>
            ))}
          </select>
        </div>
        <div>
          <label style={labelStyle}>Trạng thái</label>
          <select
            style={{ ...inputStyle, cursor: 'pointer' }}
            value={form.status}
            onChange={(e) => setForm((f) => ({ ...f, status: e.target.value as IngredientStatus }))}
          >
            <option value="ACTIVE">Đang dùng</option>
            <option value="INACTIVE">Ngừng dùng</option>
            <option value="DISCONTINUED">Ngừng kinh doanh</option>
          </select>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
        <div>
          <label style={labelStyle}>Tồn kho tối thiểu</label>
          <input
            type="number"
            step="0.001"
            style={inputStyle}
            value={form.minStockLevel ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, minStockLevel: e.target.value ? Number(e.target.value) : null }))}
            placeholder="0"
          />
        </div>
        <div>
          <label style={labelStyle}>Tồn kho tối đa</label>
          <input
            type="number"
            step="0.001"
            style={inputStyle}
            value={form.maxStockLevel ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, maxStockLevel: e.target.value ? Number(e.target.value) : null }))}
            placeholder="1000"
          />
        </div>
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
          {submitting ? 'Đang lưu...' : 'Lưu nguyên liệu'}
        </button>
      </div>
    </form>
  )
}
