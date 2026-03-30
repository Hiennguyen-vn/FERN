import type { InputHTMLAttributes } from 'react'
import { Input } from '../components/input'

interface QuantityInputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  error?: string
  helperText?: string
  label?: string
}

export function QuantityInput({ inputMode = 'decimal', min = '0.0001', step = '0.0001', ...props }: QuantityInputProps) {
  return <Input inputMode={inputMode} min={min} step={step} type="number" {...props} />
}
