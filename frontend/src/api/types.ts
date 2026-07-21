export type Classification =
  | 'CLEAN_MATCH'
  | 'UNMATCHED_INTERNAL'
  | 'UNMATCHED_SETTLEMENT'
  | 'AMOUNT_MISMATCH'
  | 'FEE_DISCREPANCY'
  | 'DUPLICATE_SETTLEMENT'
  | 'ORPHAN_REFUND'
  | 'SPLIT_SETTLEMENT'
  | 'WIDE_WINDOW_TIMING'

export interface CategorySummary {
  classification: Classification
  count: number
  totalAmount: number
}

export interface RunSummary {
  runId: number
  importedAt: string
  internalFileName: string
  settlementFileName: string
  validInternalCount: number
  validSettlementCount: number
  quarantinedCount: number
  expectedPayout: number
  actualSettled: number
  payoutDiscrepancy: number
  totalFeesReported: number
  categories: CategorySummary[]
}

export interface SettlementRow {
  networkRef: string
  merchantRef: string | null
  merchantId: string
  cardType: string | null
  cardLast4: string | null
  settledAmount: number
  interchangeFee: number
  processorFee: number
  settlementDate: string
}

export interface BreakItem {
  id: number
  classification: Classification
  reason: string
  merchantId: string
  internalTxnId: string | null
  merchantRef: string | null
  cardType: string | null
  cardLast4: string | null
  grossAmount: number | null
  transactionType: string | null
  capturedAt: string | null
  settlementRows: SettlementRow[]
}

export interface MerchantRollup {
  merchantId: string
  itemCount: number
  breakCount: number
  totalGross: number
  totalSettled: number
}

export interface QuarantineRow {
  source: 'INTERNAL' | 'SETTLEMENT'
  rowIdentifier: string
  rawContent: string
  reasons: string
}

export interface ImportResponse {
  runId: number
  alreadyImported: boolean
}
