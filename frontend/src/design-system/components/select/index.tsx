import type { SelectHTMLAttributes } from 'react'
import clsx from 'clsx'

export interface SelectOption {
  label: string
  value: string
}

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  error?: string
  label?: string
  options: SelectOption[]
  placeholder?: string
}

export function Select({ className, error, id, label, options, placeholder, ...props }: SelectProps) {
  const selectId = id ?? props.name ?? label?.toLowerCase().replace(/\s+/g, '-')

  return (
    <label className="field" htmlFor={selectId}>
      {label ? <span className="field-label">{label}</span> : null}
      <select className={clsx('input', error && 'input-error', className)} id={selectId} {...props}>
        {placeholder ? <option value="">{placeholder}</option> : null}
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      {error ? <span className="error-text">{error}</span> : null}
    </label>
  )
}
