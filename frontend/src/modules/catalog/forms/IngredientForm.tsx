import { useState } from 'react'
import { Button, Input, Select } from '@design-system/index'
import type { SelectOption } from '@design-system/index'
import type { Ingredient, IngredientStatus, IngredientUpsertRequest, UnitOfMeasure, Category } from '../model/catalog.types'

interface IngredientFormProps {
  initial?: Partial<Ingredient>
  categories: Category[]
  uoms: UnitOfMeasure[]
  onSubmit: (data: IngredientUpsertRequest) => Promise<void>
  onCancel: () => void
  submitting: boolean
}

const statusOptions: SelectOption[] = [
  { label: 'Đang dùng', value: 'ACTIVE' },
  { label: 'Ngừng dùng', value: 'INACTIVE' },
  { label: 'Ngừng kinh doanh', value: 'DISCONTINUED' },
]

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

  const categoryOptions: SelectOption[] = categories.map((category) => ({
    label: category.name,
    value: category.code,
  }))

  const uomOptions: SelectOption[] = uoms.map((uom) => ({
    label: `${uom.name} (${uom.code})`,
    value: uom.code,
  }))

  return (
    <form
      className="catalog-form"
      onSubmit={async (e) => {
        e.preventDefault()
        await onSubmit(form)
      }}
    >
      <div className="catalog-form-grid-2">
        <Input
          disabled={!!initial?.id}
          label="Mã *"
          onChange={(e) => setForm((f) => ({ ...f, code: e.target.value.toUpperCase() }))}
          placeholder="ING-001"
          required
          value={form.code}
        />
        <Select
          label="ĐVT cơ bản *"
          onChange={(e) => setForm((f) => ({ ...f, baseUomCode: e.target.value }))}
          options={uomOptions}
          required
          value={form.baseUomCode}
        />
      </div>

      <Input
        label="Tên nguyên liệu *"
        onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
        placeholder="Cà phê robusta"
        required
        value={form.name}
      />

      <div className="catalog-form-grid-2">
        <Select
          label="Danh mục"
          onChange={(e) => setForm((f) => ({ ...f, categoryCode: e.target.value || null }))}
          options={categoryOptions}
          placeholder="— Không có —"
          value={form.categoryCode ?? ''}
        />
        <Select
          label="Trạng thái"
          onChange={(e) => setForm((f) => ({ ...f, status: e.target.value as IngredientStatus }))}
          options={statusOptions}
          value={form.status}
        />
      </div>

      <div className="catalog-form-grid-2">
        <Input
          label="Tồn kho tối thiểu"
          onChange={(e) => setForm((f) => ({ ...f, minStockLevel: e.target.value ? Number(e.target.value) : null }))}
          placeholder="0"
          step="0.001"
          type="number"
          value={form.minStockLevel ?? ''}
        />
        <Input
          label="Tồn kho tối đa"
          onChange={(e) => setForm((f) => ({ ...f, maxStockLevel: e.target.value ? Number(e.target.value) : null }))}
          placeholder="1000"
          step="0.001"
          type="number"
          value={form.maxStockLevel ?? ''}
        />
      </div>

      <div className="catalog-form-actions">
        <Button onClick={onCancel} type="button" variant="secondary">
          Hủy
        </Button>
        <Button disabled={submitting} type="submit">
          {submitting ? 'Đang lưu...' : 'Lưu nguyên liệu'}
        </Button>
      </div>
    </form>
  )
}
