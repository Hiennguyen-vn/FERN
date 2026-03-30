import type { IamRoleStatus, IamUserStatus } from '../model/iam.types'
import { iamUiPolicy } from '../services/iamUiPolicy.service'

const USER_STATUS_LABELS: Record<IamUserStatus, string> = {
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
  LOCKED: 'Locked',
  SUSPENDED: 'Suspended',
}

const ROLE_STATUS_LABELS: Record<IamRoleStatus, string> = {
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
}

export function IamUserStatusBadge({ status }: { status: IamUserStatus }) {
  return <span className={iamUiPolicy.userStatusTone(status)}>{USER_STATUS_LABELS[status]}</span>
}

export function IamRoleStatusBadge({ status }: { status: IamRoleStatus }) {
  return <span className={iamUiPolicy.roleStatusTone(status)}>{ROLE_STATUS_LABELS[status]}</span>
}
