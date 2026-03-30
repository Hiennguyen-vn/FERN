import type { FernPrincipal } from '@core/auth/auth.types'
import { hasAnyPermissions } from '@core/permissions/permission.checker'
import { permissionConstants } from '@core/permissions/permission.constants'
import type { RegionalContextResolution } from '../model/regionalOps.types'

const regionalOpsPermissions = [permissionConstants.org.regionRead, permissionConstants.org.outletRead]

export function canOpenRegionalDashboard(principal: FernPrincipal | null) {
  return hasAnyPermissions(principal, regionalOpsPermissions)
}

export function canOpenOutletSummary(principal: FernPrincipal | null) {
  return hasAnyPermissions(principal, regionalOpsPermissions)
}

export function canOpenOutletDetail(principal: FernPrincipal | null) {
  return hasAnyPermissions(principal, regionalOpsPermissions)
}

export function canSeeRegionalOpsNavigation(principal: FernPrincipal | null) {
  return hasAnyPermissions(principal, regionalOpsPermissions)
}

export function resolveRegionalContext(
  principal: FernPrincipal | null,
  selectedRegionId: number | null,
  regionIds: number[],
): RegionalContextResolution {
  if (!principal) {
    return {
      message: 'Bạn cần đăng nhập để mở Regional Ops.',
      resolvedRegionId: null,
      status: 'missing',
    }
  }

  if (selectedRegionId && regionIds.includes(selectedRegionId)) {
    return {
      message: null,
      resolvedRegionId: selectedRegionId,
      status: 'resolved',
    }
  }

  if (!selectedRegionId && regionIds.length === 1) {
    return {
      message: null,
      resolvedRegionId: regionIds[0],
      status: 'resolved',
    }
  }

  if (!selectedRegionId && regionIds.length > 1) {
    return {
      message: 'Chọn một region cụ thể trong app shell để mở dashboard và outlet summary theo góc nhìn regional.',
      resolvedRegionId: null,
      status: 'missing',
    }
  }

  if (!selectedRegionId && regionIds.length === 0 && principal.scopeRoots.system) {
    return {
      message: 'Tài khoản system-scoped cần chọn region cụ thể trước khi mở Regional Ops. Màn này không query toàn bộ hệ thống một cách mù.',
      resolvedRegionId: null,
      status: 'system-unscoped',
    }
  }

  if (!selectedRegionId && regionIds.length === 0) {
    return {
      message: 'Scope hiện tại không có region nào để giám sát ở Regional Ops.',
      resolvedRegionId: null,
      status: 'missing',
    }
  }

  return {
    message: 'Region đang chọn không còn nằm trong scope hiện tại. Hãy chọn lại region hợp lệ trong app shell.',
    resolvedRegionId: null,
    status: 'invalid',
  }
}
