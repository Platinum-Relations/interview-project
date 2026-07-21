import type { Classification } from '../api/types'

const currencyFormatter = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
})

export function formatMoney(amount: number | null | undefined): string {
  if (amount === null || amount === undefined) {
    return '-'
  }
  return currencyFormatter.format(amount)
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) {
    return '-'
  }
  return new Date(iso).toLocaleString('en-US', { timeZone: 'UTC', hour12: false }) + ' UTC'
}

export const CLASSIFICATION_LABELS: Record<Classification, string> = {
  CLEAN_MATCH: 'Cleanly matched',
  UNMATCHED_INTERNAL: 'Unmatched - internal',
  UNMATCHED_SETTLEMENT: 'Unmatched - settlement',
  AMOUNT_MISMATCH: 'Amount mismatch',
  FEE_DISCREPANCY: 'Fee discrepancy',
  DUPLICATE_SETTLEMENT: 'Duplicate settlement',
  ORPHAN_REFUND: 'Orphan refund',
  SPLIT_SETTLEMENT: 'Split settlement',
  WIDE_WINDOW_TIMING: 'Wide-window timing',
}

export const BREAK_CATEGORIES: Classification[] = [
  'UNMATCHED_INTERNAL',
  'UNMATCHED_SETTLEMENT',
  'AMOUNT_MISMATCH',
  'FEE_DISCREPANCY',
  'DUPLICATE_SETTLEMENT',
  'ORPHAN_REFUND',
  'SPLIT_SETTLEMENT',
  'WIDE_WINDOW_TIMING',
]
