import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { useFieldErrors } from '@core/api/useFieldErrors'
import { usePrincipal } from '@core/auth/auth.selectors'
import {
  Button,
  FormActions,
  FormField,
  FormSection,
  Input,
  PermissionDeniedInline,
  Select,
} from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useCreateSupplier } from '../hooks/useSuppliers'
import type { SupplierUpsertPayload } from '../model/procurement.types'
import { canWriteSuppliers } from '../services/procurementPermission.service'

export function SupplierCreatePage() {
  usePageTitle('Add Supplier')
  const principal = usePrincipal()
  const canWrite = canWriteSuppliers(principal)
  const navigate = useNavigate()
  const createMutation = useCreateSupplier()
  const { getError: getServerError } = useFieldErrors(createMutation.error)
  const [form, setForm] = useState<SupplierUpsertPayload>({
    supplierCode: '',
    name: '',
    status: 'ACTIVE',
  })

  async function handleSubmit() {
    const result = await createMutation.mutateAsync(form)
    void navigate(`/procurement/suppliers/${result.id}`)
  }

  if (!canWrite) {
    return (
      <DashboardLayout description="Tạo nhà cung cấp mới." title="Add Supplier">
        <PermissionDeniedInline message="You need procurement.supplier.write to add suppliers." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout description="Tạo nhà cung cấp mới trong hệ thống procurement." title="Add Supplier">
      <FormSection title="Supplier information">
        <div className="field-grid">
          <FormField error={getServerError('supplierCode')} label="Supplier code" required>
            <Input
              onChange={(e) => setForm((f) => ({ ...f, supplierCode: e.target.value }))}
              placeholder="e.g. SUP-001"
              value={form.supplierCode}
            />
          </FormField>
          <FormField error={getServerError('name')} label="Name" required>
            <Input
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
              placeholder="Supplier name"
              value={form.name}
            />
          </FormField>
          <FormField error={getServerError('email')} label="Email">
            <Input
              onChange={(e) => setForm((f) => ({ ...f, email: e.target.value || null }))}
              placeholder="contact@supplier.com"
              type="email"
              value={form.email ?? ''}
            />
          </FormField>
          <FormField error={getServerError('phone')} label="Phone">
            <Input
              onChange={(e) => setForm((f) => ({ ...f, phone: e.target.value || null }))}
              placeholder="Phone number"
              value={form.phone ?? ''}
            />
          </FormField>
          <FormField error={getServerError('taxCode')} label="Tax code">
            <Input
              onChange={(e) => setForm((f) => ({ ...f, taxCode: e.target.value || null }))}
              placeholder="Tax identification number"
              value={form.taxCode ?? ''}
            />
          </FormField>
          <FormField error={getServerError('address')} label="Address">
            <Input
              onChange={(e) => setForm((f) => ({ ...f, address: e.target.value || null }))}
              placeholder="Business address"
              value={form.address ?? ''}
            />
          </FormField>
          <FormField error={getServerError('status')} label="Status">
            <Select
              onChange={(e) => setForm((f) => ({ ...f, status: e.target.value as SupplierUpsertPayload['status'] }))}
              options={[
                { label: 'Active', value: 'ACTIVE' },
                { label: 'Inactive', value: 'INACTIVE' },
                { label: 'Suspended', value: 'SUSPENDED' },
              ]}
              value={form.status ?? 'ACTIVE'}
            />
          </FormField>
        </div>
      </FormSection>

      <FormActions>
        <Button onClick={() => void navigate(-1)} variant="secondary">
          Cancel
        </Button>
        <Button
          disabled={!form.supplierCode || !form.name}
          loading={createMutation.isPending}
          onClick={() => void handleSubmit()}
        >
          Create supplier
        </Button>
      </FormActions>
    </DashboardLayout>
  )
}
