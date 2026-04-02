/**
 * UNPUBLISHED WRITE FLOWS — Backend contract reference.
 *
 * These backend write endpoints are confirmed real (validated, tested server-side)
 * but have NO frontend create/edit UI yet. Each entry documents the backend contract
 * so the next engineer starts from the correct spec rather than guessing.
 *
 * When a flow is published, move its entry to the relevant validation service.
 *
 * Last audited: 2026-04-01 (backend-validation alignment passes).
 */

// ─── HR service ────────────────────────────────────────────────────────────────

/**
 * POST /employees
 * @Valid CreateEmployeeRequest
 *
 * Required: fullName @NotBlank
 * Optional: employeeCode, dob, gender, email, phone, status, hiredAt, userAccountId
 *
 * Permission: hr.employee.write (or equivalent scope)
 * No Idempotency-Key required on this endpoint.
 */
export const UNPUBLISHED_HR_CREATE_EMPLOYEE = 'hr:POST:/employees' as const

/**
 * POST /employee-contracts
 * @Valid CreateContractRequest
 *
 * Required:
 *   employeeId  @NotNull Long
 *   employmentType @NotBlank String
 *   salaryType  @NotBlank String
 *   baseSalary  @NotNull @DecimalMin("0.00") BigDecimal
 *   startDate   @NotNull LocalDate
 *
 * Optional: regionId, taxCode, contractStatus, endDate
 *
 * Class-level: @AssertTrue — endDate must be on or after startDate (when set)
 *
 * Permission: hr.contract.write
 */
export const UNPUBLISHED_HR_CREATE_CONTRACT = 'hr:POST:/employee-contracts' as const

/**
 * POST /employee-assignments
 * @Valid CreateAssignmentRequest
 *
 * Required:
 *   employeeId   @NotNull Long
 *   regionId     @NotNull Long
 *   outletId     @NotNull Long
 *   positionTitle @NotBlank String
 *   startDate    @NotNull LocalDate
 *
 * Optional: endDate, primaryAssignment, status
 *
 * Class-level: @AssertTrue — endDate must be on or after startDate (when set)
 *
 * Permission: hr.assignment.write
 */
export const UNPUBLISHED_HR_CREATE_ASSIGNMENT = 'hr:POST:/employee-assignments' as const

/**
 * POST /shift-assignments
 * @Valid CreateShiftAssignmentRequest
 *
 * Required:
 *   shiftScheduleId @NotNull Long
 *   employeeId      @NotNull Long
 *
 * Optional: assignedRole, note
 *
 * Permission: hr.schedule.write
 */
export const UNPUBLISHED_HR_CREATE_SHIFT_ASSIGNMENT = 'hr:POST:/shift-assignments' as const

/**
 * POST /attendance-events
 * @Valid RecordAttendanceEventRequest
 * Required header: Idempotency-Key
 *
 * Required:
 *   employeeId       @NotNull Long
 *   regionId         @NotNull Long
 *   outletId         @NotNull Long
 *   shiftAssignmentId @NotNull Long
 *   eventType        @NotBlank @Pattern("CLOCK_IN|CLOCK_OUT|BREAK_START|BREAK_END")
 *   eventTime        @NotNull Instant (ISO string)
 *
 * Optional: sourceSystem
 *
 * Note: frontend MyAttendancePage records clock-in/out but the create form
 * validation is inline — should migrate to a shared validator using this contract.
 */
export const UNPUBLISHED_HR_RECORD_ATTENDANCE = 'hr:POST:/attendance-events' as const

// ─── Catalog service ───────────────────────────────────────────────────────────

/**
 * All catalog write endpoints (product create/update/archive, ingredient create/update,
 * pricing upsert, recipe ingredient management) are currently published as read-only
 * in the frontend (ReadonlyBanner on all catalog pages).
 *
 * Key contracts when publishing:
 *   - Ingredient writes require system scope (no region/outlet scope)
 *   - ProductPriceUpsertRequest.effectiveFrom @NotNull Instant
 *   - TaxRateUpsertRequest: name @NotBlank, ratePercent @NotNull @DecimalMin("0.00")
 *   - UomConversionRequest uses field name 'conversionFactor' (not 'factor')
 *
 * Documented in: catalog.types.ts, catalog.api.ts
 */
export const UNPUBLISHED_CATALOG_WRITES = 'catalog:ALL_WRITE_ENDPOINTS' as const

// ─── Org service ──────────────────────────────────────────────────────────────

/**
 * PUT/PATCH /regions — Region create and edit workflow.
 * RegionDetailPage has ReadonlyBanner: "Chưa publish create/edit workflow".
 * Audit backend UpsertRegionRequest before publishing.
 */
export const UNPUBLISHED_ORG_REGION_EDIT = 'org:PUT:/regions/:id' as const
