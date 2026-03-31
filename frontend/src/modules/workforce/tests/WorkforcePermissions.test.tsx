/**
 * Workforce permission correctness tests.
 *
 * Covers:
 * - review-only users (hr.attendance.review, no hr.attendance.write)
 * - write-only users (hr.attendance.write, no hr.attendance.review)
 * - users with both permissions
 * - users with neither permission (should be denied at module level)
 * - canAccessWorkforceModule helper mirrors router guard intent
 * - RequireAnyPermission guard allows review-only and write-only users
 * - RequireAnyPermission guard blocks users with no workforce permission
 */
import { Route, Routes } from 'react-router-dom'
import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { RequireAnyPermission } from '@app/guards/RequireAnyPermission'
import { permissionConstants } from '@core/permissions/permission.constants'
import {
  canAccessWorkforceModule,
  canApproveAttendance,
  canRecordAttendance,
} from '../services/workforcePermission.service'
import { createTestPrincipal } from '@shared/test-utils/scopeTestHelpers'

// ─── Service-level unit tests ────────────────────────────────────────────────

describe('workforcePermission.service', () => {
  it('canRecordAttendance: true when principal has attendanceWrite', () => {
    const principal = createTestPrincipal({ permissions: ['hr.attendance.write'] })
    expect(canRecordAttendance(principal)).toBe(true)
  })

  it('canRecordAttendance: false for review-only user', () => {
    const principal = createTestPrincipal({ permissions: ['hr.attendance.review'] })
    expect(canRecordAttendance(principal)).toBe(false)
  })

  it('canApproveAttendance: true when principal has attendanceReview', () => {
    const principal = createTestPrincipal({ permissions: ['hr.attendance.review'] })
    expect(canApproveAttendance(principal)).toBe(true)
  })

  it('canApproveAttendance: false for write-only user', () => {
    const principal = createTestPrincipal({ permissions: ['hr.attendance.write'] })
    expect(canApproveAttendance(principal)).toBe(false)
  })

  it('canAccessWorkforceModule: true with only attendanceWrite', () => {
    const principal = createTestPrincipal({
      permissions: [permissionConstants.hr.attendanceWrite],
    })
    expect(canAccessWorkforceModule(principal)).toBe(true)
  })

  it('canAccessWorkforceModule: true with only attendanceReview', () => {
    const principal = createTestPrincipal({
      permissions: [permissionConstants.hr.attendanceReview],
    })
    expect(canAccessWorkforceModule(principal)).toBe(true)
  })

  it('canAccessWorkforceModule: true with both permissions', () => {
    const principal = createTestPrincipal({
      permissions: [permissionConstants.hr.attendanceWrite, permissionConstants.hr.attendanceReview],
    })
    expect(canAccessWorkforceModule(principal)).toBe(true)
  })

  it('canAccessWorkforceModule: false with no workforce permissions', () => {
    const principal = createTestPrincipal({ permissions: ['report.read', 'catalog.product.read'] })
    expect(canAccessWorkforceModule(principal)).toBe(false)
  })

  it('canAccessWorkforceModule: false for null principal', () => {
    expect(canAccessWorkforceModule(null)).toBe(false)
  })
})

// ─── RequireAnyPermission guard integration tests ────────────────────────────

function ProtectedPage() {
  return <div>workforce content</div>
}

function UnauthorizedPage() {
  return <div>unauthorized</div>
}

function renderGuardedWorkforce(permissions: string[]) {
  setAuthenticatedSession({ principal: { permissions } })
  return renderWithProviders(
    <Routes>
      <Route
        path="/workforce"
        element={
          <RequireAnyPermission
            permissions={[
              permissionConstants.hr.attendanceWrite,
              permissionConstants.hr.attendanceReview,
            ]}
          />
        }
      >
        <Route index element={<ProtectedPage />} />
      </Route>
      <Route path="/unauthorized" element={<UnauthorizedPage />} />
    </Routes>,
    { route: '/workforce' },
  )
}

describe('RequireAnyPermission — workforce route guard', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
  })

  it('allows a review-only user (hr.attendance.review)', () => {
    renderGuardedWorkforce(['hr.attendance.review'])
    expect(screen.getByText('workforce content')).toBeInTheDocument()
  })

  it('allows a write-only user (hr.attendance.write)', () => {
    renderGuardedWorkforce(['hr.attendance.write'])
    expect(screen.getByText('workforce content')).toBeInTheDocument()
  })

  it('allows a user with both attendance permissions', () => {
    renderGuardedWorkforce(['hr.attendance.write', 'hr.attendance.review'])
    expect(screen.getByText('workforce content')).toBeInTheDocument()
  })

  it('blocks a user with no attendance permission', () => {
    renderGuardedWorkforce(['report.read', 'catalog.product.read'])
    expect(screen.getByText('unauthorized')).toBeInTheDocument()
  })

  it('blocks an unauthenticated / null-permission user', () => {
    renderGuardedWorkforce([])
    expect(screen.getByText('unauthorized')).toBeInTheDocument()
  })
})

// ─── System-scope user access ────────────────────────────────────────────────

describe('system-scope user workforce access', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
  })

  it('system-scope user with attendanceReview can access workforce module', () => {
    const principal = createTestPrincipal({
      permissions: ['hr.attendance.review'],
      scopeRoots: { system: true, regions: [], outlets: [] },
    })
    expect(canAccessWorkforceModule(principal)).toBe(true)
    expect(canApproveAttendance(principal)).toBe(true)
    expect(canRecordAttendance(principal)).toBe(false)
  })

  it('system-scope user without attendance permissions is still denied module access', () => {
    const principal = createTestPrincipal({
      permissions: ['report.read'],
      scopeRoots: { system: true, regions: [], outlets: [] },
    })
    expect(canAccessWorkforceModule(principal)).toBe(false)
  })
})
