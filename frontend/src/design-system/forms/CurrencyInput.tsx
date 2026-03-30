import type { InputHTMLAttributes } from 'react'
import { Input } from '../components/input'

interface CurrencyInputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  error?: string
  helperText?: string
  label?: string
}

export function CurrencyInput({ inputMode = 'decimal', min = '0', step = '0.01', ...props }: CurrencyInputProps) {
  return <Input inputMode={inputMode} min={min} step={step} type="number" {...props} />
}
