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

export type MatchMethod = 'MERCHANT_REF' | 'MERCHANT_CARD_NET' | 'UNMATCHED'

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

/** Source-shaped ledger row (column names match internal_transactions.csv). */
export interface LedgerSourceRow {
  internal_txn_id: string
  merchant_id: string
  merchant_ref: string | null
  card_type: string | null
  card_last4: string | null
  gross_amount: number
  currency: string
  type: string | null
  captured_at: string | null
  classification: Classification
  match_method: MatchMethod
  match_method_label: string
  reason: string
  paired_settlements: SettlementSourceRow[]
}

/** Source-shaped settlement row (field names match processor_settlement.json). */
export interface SettlementSourceRow {
  network_ref: string
  merchant_ref: string | null
  merchant_id: string
  card_last4: string | null
  card_type: string | null
  settled_amount: number
  interchange_fee: number
  processor_fee: number
  currency: string
  settlement_date: string | null
  classification: Classification
  match_method: MatchMethod
  match_method_label: string
  reason: string
  paired_ledger: LedgerSourceRow | null
}

export type SourceFocus =
  | { tab: 'ledger'; id: string }
  | { tab: 'settlement'; id: string }
  | { tab: 'quarantine'; id: string }
  | null
