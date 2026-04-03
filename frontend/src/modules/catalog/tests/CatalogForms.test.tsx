import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { IngredientForm } from '../forms/IngredientForm'
import { ProductForm } from '../forms/ProductForm'

describe('Catalog forms', () => {
  it('submits normalized payload from ProductForm', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockResolvedValue(undefined)

    render(
      <ProductForm
        categories={[{ code: 'BEVERAGE', name: 'Beverage' }]}
        onCancel={vi.fn()}
        onSubmit={onSubmit}
        submitting={false}
      />,
    )

    await user.type(screen.getByLabelText('Mã sản phẩm *'), 'cf-001')
    await user.type(screen.getByLabelText('Tên sản phẩm *'), 'Iced Coffee')
    await user.selectOptions(screen.getByLabelText('Trạng thái *'), 'DRAFT')
    await user.selectOptions(screen.getByLabelText('Danh mục'), 'BEVERAGE')
    await user.type(screen.getByLabelText('Mô tả'), 'Cold brew with milk')
    await user.click(screen.getByRole('button', { name: 'Lưu sản phẩm' }))

    expect(screen.getByLabelText('Mã sản phẩm *')).toHaveValue('CF-001')
    expect(onSubmit).toHaveBeenCalledWith({
      code: 'CF-001',
      name: 'Iced Coffee',
      categoryCode: 'BEVERAGE',
      status: 'DRAFT',
      imageUrl: null,
      description: 'Cold brew with milk',
    })
  })

  it('submits numeric and uppercase values from IngredientForm', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockResolvedValue(undefined)

    render(
      <IngredientForm
        categories={[{ code: 'COFFEE', name: 'Coffee' }]}
        onCancel={vi.fn()}
        onSubmit={onSubmit}
        submitting={false}
        uoms={[{ code: 'KG', name: 'Kilogram', symbol: 'kg' }]}
      />,
    )

    await user.type(screen.getByLabelText('Mã *'), 'ing-001')
    await user.type(screen.getByLabelText('Tên nguyên liệu *'), 'Robusta')
    await user.selectOptions(screen.getByLabelText('Danh mục'), 'COFFEE')
    await user.selectOptions(screen.getByLabelText('Trạng thái'), 'INACTIVE')
    await user.clear(screen.getByLabelText('Tồn kho tối thiểu'))
    await user.type(screen.getByLabelText('Tồn kho tối thiểu'), '1.5')
    await user.clear(screen.getByLabelText('Tồn kho tối đa'))
    await user.type(screen.getByLabelText('Tồn kho tối đa'), '12')
    await user.click(screen.getByRole('button', { name: 'Lưu nguyên liệu' }))

    expect(screen.getByLabelText('Mã *')).toHaveValue('ING-001')
    expect(onSubmit).toHaveBeenCalledWith({
      code: 'ING-001',
      name: 'Robusta',
      categoryCode: 'COFFEE',
      baseUomCode: 'KG',
      minStockLevel: 1.5,
      maxStockLevel: 12,
      status: 'INACTIVE',
    })
  })
})
