import type { RunSummary } from '../api/types'
import { formatDateTime, formatMoney } from '../lib/format'
import { logAction } from '../lib/log'

interface Props {
  summary: RunSummary
  onOpenSourceData: () => void
}

export function SummaryCards({ summary, onOpenSourceData }: Props) {
  const discrepancyClass = summary.payoutDiscrepancy === 0 ? '' : 'value-alert'
  return (
    <section className="panel" id="section-summary">
      <div className="panel-heading">
        <h2>Run {summary.runId}</h2>
        <span className="panel-subtitle">
          {summary.internalFileName} + {summary.settlementFileName}, imported {formatDateTime(summary.importedAt)}
        </span>
      </div>
      <div className="cards">
        <div className="card">
          <span className="card-label">Expected payout</span>
          <span className="card-value">{formatMoney(summary.expectedPayout)}</span>
          <span className="card-hint">fee-adjusted net over valid ledger rows</span>
        </div>
        <div className="card">
          <span className="card-label">Actually settled</span>
          <span className="card-value">{formatMoney(summary.actualSettled)}</span>
          <span className="card-hint">sum of all valid settlement rows</span>
        </div>
        <div className="card">
          <span className="card-label">Discrepancy</span>
          <span className={`card-value ${discrepancyClass}`}>{formatMoney(summary.payoutDiscrepancy)}</span>
          <span className="card-hint">settled minus expected</span>
        </div>
        <div className="card">
          <span className="card-label">Fees deducted</span>
          <span className="card-value">{formatMoney(summary.totalFeesReported)}</span>
          <span className="card-hint">interchange + processor, as reported</span>
        </div>
        <button
          type="button"
          className="card card-clickable"
          onClick={() => {
            console.log('[ui] summary.openSourceData clicked runId=' + summary.runId)
            logAction('summary.openSourceData', { runId: summary.runId })
            onOpenSourceData()
          }}
        >
          <span className="card-label">Rows processed</span>
          <span className="card-value">
            {summary.validInternalCount + summary.validSettlementCount}
          </span>
          <span className="card-hint">
            {summary.validInternalCount} ledger, {summary.validSettlementCount} settlement,{' '}
            {summary.quarantinedCount} quarantined — click to browse source data
          </span>
        </button>
      </div>
    </section>
  )
}
