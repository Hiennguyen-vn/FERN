import { useState } from 'react'
import { Button, Input, Select, Textarea } from '@design-system/index'
import type { SelectOption } from '@design-system/index'
import type { Product, ProductStatus, ProductUpsertRequest } from '../model/catalog.types'

interface ProductFormProps {
  initial?: Partial<Product>
  categories: { code: string; name: string }[]
  onSubmit: (data: ProductUpsertRequest) => Promise<void>
  onCancel: () => void
  submitting: boolean
}

const statusOptions: SelectOption[] = [
  { label: 'Draft', value: 'DRAFT' },
  { label: 'Đang bán', value: 'ACTIVE' },
  { label: 'Ngừng bán', value: 'INACTIVE' },
  { label: 'Ngừng kinh doanh', value: 'DISCONTINUED' },
]

export function ProductForm({ initial, categories, onSubmit, onCancel, submitting }: ProductFormProps) {
  const [form, setForm] = useState<ProductUpsertRequest>({
    code: initial?.code ?? '',
    name: initial?.name ?? '',
    categoryCode: initial?.categoryCode ?? null,
    status: initial?.status ?? 'ACTIVE',
    imageUrl: initial?.imageUrl ?? null,
    description: initial?.description ?? null,
  })

  const categoryOptions: SelectOption[] = categories.map((category) => ({
    label: category.name,
    value: category.code,
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
          label="Mã sản phẩm *"
          onChange={(e) => setForm((f) => ({ ...f, code: e.target.value.toUpperCase() }))}
          placeholder="PROD-001"
          required
          value={form.code}
        />
        <Select
          label="Trạng thái *"
          onChange={(e) => setForm((f) => ({ ...f, status: e.target.value as ProductStatus }))}
          options={statusOptions}
          required
          value={form.status}
        />
      </div>

      <Input
        label="Tên sản phẩm *"
        maxLength={150}
        onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
        placeholder="Cà phê sữa đá"
        required
        value={form.name}
      />

      <Select
        label="Danh mục"
        onChange={(e) => setForm((f) => ({ ...f, categoryCode: e.target.value || null }))}
        options={categoryOptions}
        placeholder="— Không có danh mục —"
        value={form.categoryCode ?? ''}
      />

      <Textarea
        className="catalog-form-textarea"
        label="Mô tả"
        onChange={(e) => setForm((f) => ({ ...f, description: e.target.value || null }))}
        placeholder="Mô tả ngắn về sản phẩm..."
        rows={3}
        value={form.description ?? ''}
      />

      <div className="catalog-form-actions">
        <Button onClick={onCancel} type="button" variant="secondary">
          Hủy
        </Button>
        <Button disabled={submitting} type="submit">
          {submitting ? 'Đang lưu...' : 'Lưu sản phẩm'}
        </Button>
      </div>
    </form>
  )
}
