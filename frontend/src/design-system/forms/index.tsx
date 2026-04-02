import type { ReactNode } from 'react'
import clsx from 'clsx'

// ── FormField ──
interface FormFieldProps {
  children: ReactNode
  error?: string
  label?: string
  required?: boolean
  hint?: string
}

export function FormField({ children, error, label, required, hint }: FormFieldProps) {
  return (
    <div className="space-y-2">
      {label ? (
        <label className="flex items-center gap-1">
          <span className="font-label text-xs font-bold text-on-surface-variant uppercase tracking-widest">
            {label}
          </span>
          {required ? <span className="text-error text-sm">*</span> : null}
        </label>
      ) : null}
      {children}
      {hint && !error ? (
        <p className="text-xs text-on-surface-variant">{hint}</p>
      ) : null}
      {error ? (
        <p className="text-xs text-error font-medium flex items-center gap-1">
          <span className="material-symbols-outlined text-xs">error</span>
          {error}
        </p>
      ) : null}
    </div>
  )
}

// ── FormSection ──
interface FormSectionProps {
  children: ReactNode
  title?: string
  description?: string
  className?: string
}

export function FormSection({ children, title, description, className }: FormSectionProps) {
  return (
    <section className={clsx('bg-surface-container-lowest rounded-xl p-6 shadow-sm space-y-6', className)}>
      {title ? (
        <div className="border-b border-surface-container pb-4">
          <h3 className="font-headline font-bold text-lg text-on-surface">{title}</h3>
          {description ? (
            <p className="text-sm text-on-surface-variant mt-1">{description}</p>
          ) : null}
        </div>
      ) : null}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">{children}</div>
    </section>
  )
}

// ── FormActions ──
interface FormActionsProps {
  children?: ReactNode
  align?: 'start' | 'end' | 'between'
  primaryAction?: ReactNode
  secondaryAction?: ReactNode
}

export function FormActions({ children, align = 'end', primaryAction, secondaryAction }: FormActionsProps) {
  const alignClass = {
    start: 'justify-start',
    end: 'justify-end',
    between: 'justify-between',
  }
  const content = children ?? (
    <>
      {secondaryAction}
      {primaryAction}
    </>
  )

  return (
    <div className={clsx('flex items-center gap-3 pt-6 border-t border-surface-container', alignClass[align])}>
      {content}
    </div>
  )
}

// ── CurrencyInput ──
interface CurrencyInputProps {
  value?: string | number
  onChange?: (value: string) => void
  currency?: string
  error?: string
  label?: string
  name?: string
  disabled?: boolean
}

export function CurrencyInput({ value, onChange, currency = '£', error, label, name, disabled }: CurrencyInputProps) {
  return (
    <FormField label={label} error={error}>
      <div className="relative">
        <span className="absolute inset-y-0 left-0 pl-4 flex items-center text-on-surface-variant font-bold text-sm pointer-events-none">
          {currency}
        </span>
        <input
          className={clsx(
            'w-full bg-surface-container-low border-none rounded-lg pl-8 pr-4 py-3.5',
            'focus:ring-2 focus:ring-primary-container text-on-surface font-body text-sm transition-all',
            'text-right font-mono',
            error && 'ring-2 ring-error',
          )}
          disabled={disabled}
          name={name}
          onChange={(e) => onChange?.(e.target.value)}
          type="text"
          value={value}
        />
      </div>
    </FormField>
  )
}

// ── QuantityInput ──
interface QuantityInputProps {
  value?: number
  onChange?: (value: number) => void
  min?: number
  max?: number
  step?: number
  error?: string
  label?: string
  name?: string
  disabled?: boolean
}

export function QuantityInput({ value = 0, onChange, min = 0, max, step = 1, error, label, name, disabled }: QuantityInputProps) {
  return (
    <FormField label={label} error={error}>
      <div className="flex items-center gap-1">
        <button
          className="w-10 h-10 bg-surface-container-high rounded-lg flex items-center justify-center hover:bg-surface-container-highest transition-colors disabled:opacity-50"
          disabled={disabled || value <= min}
          onClick={() => onChange?.(Math.max(min, value - step))}
          type="button"
        >
          <span className="material-symbols-outlined text-sm">remove</span>
        </button>
        <input
          className="w-20 text-center bg-surface-container-low border-none rounded-lg py-3 text-sm font-mono font-bold focus:ring-2 focus:ring-primary-container"
          disabled={disabled}
          max={max}
          min={min}
          name={name}
          onChange={(e) => onChange?.(Number(e.target.value))}
          step={step}
          type="number"
          value={value}
        />
        <button
          className="w-10 h-10 bg-surface-container-high rounded-lg flex items-center justify-center hover:bg-surface-container-highest transition-colors disabled:opacity-50"
          disabled={disabled || (max !== undefined && value >= max)}
          onClick={() => onChange?.(max !== undefined ? Math.min(max, value + step) : value + step)}
          type="button"
        >
          <span className="material-symbols-outlined text-sm">add</span>
        </button>
      </div>
    </FormField>
  )
}

// ── MaskedField ──
type MaskedMode = 'hidden' | 'masked' | 'readonly-visible' | 'fully-visible'

interface MaskedFieldProps {
  label?: string
  value?: string
  displayValue?: string
  error?: string
  className?: string
  mode?: MaskedMode
  helperText?: string
}

export function MaskedField({ label, value, displayValue, error, className, mode = 'fully-visible', helperText }: MaskedFieldProps) {
  const isMasked = mode === 'masked'
  const isHidden = mode === 'hidden'
  const shown = displayValue ?? value

  return (
    <FormField label={label} error={error}>
      {isHidden ? (
        <div className={clsx('px-4 py-3.5 bg-surface-container-low rounded-lg text-sm text-on-surface-variant italic', className)}>
          Hidden by policy
        </div>
      ) : (
        <div
          className={clsx('px-4 py-3.5 bg-surface-container-low rounded-lg text-sm text-on-surface font-mono', className)}
          style={isMasked ? { filter: 'blur(4px)', userSelect: 'none', pointerEvents: 'none' } : undefined}
          aria-hidden={isMasked || undefined}
        >
          {shown}
        </div>
      )}
      {helperText ? <p className="text-xs text-on-surface-variant mt-1">{helperText}</p> : null}
    </FormField>
  )
}
