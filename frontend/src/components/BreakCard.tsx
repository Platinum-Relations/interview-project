import type { BreakItem, SourceFocus } from '../api/types'
import { CLASSIFICATION_LABELS, formatDateTime, formatMoney } from '../lib/format'
import { logAction } from '../lib/log'

interface Props {
  item: BreakItem
  onOpenSource: (focus: NonNullable<SourceFocus>) => void
}

export function BreakCard({ item, onOpenSource }: Props) {
  return (
    <article className="break-card">
      <header className="break-header">
        <span className={`badge badge-${item.classification.toLowerCase()}`}>
          {CLASSIFICATION_LABELS[item.classification]}
        </span>
        <span className="break-merchant">{item.merchantId}</span>
      </header>
      <p className="break-reason">{item.reason}</p>
      <div className="break-sides">
        <div className="break-side">
          <h4>Internal ledger</h4>
          {item.internalTxnId ? (
            <dl>
              <dt>Transaction</dt>
              <dd>
                <button
                  type="button"
                  className="link-button"
                  onClick={() => {
                    logAction('break.openLedgerSource', { internalTxnId: item.internalTxnId })
                    onOpenSource({ tab: 'ledger', id: item.internalTxnId! })
                  }}
                >
                  {item.internalTxnId}
                </button>
              </dd>
              <dt>Reference</dt>
              <dd>{item.merchantRef ?? '-'}</dd>
              <dt>Card</dt>
              <dd>
                {item.cardType ?? '-'} ****{item.cardLast4 ?? ''}
              </dd>
              <dt>Type</dt>
              <dd>{item.transactionType}</dd>
              <dt>Gross</dt>
              <dd>{formatMoney(item.grossAmount)}</dd>
              <dt>Captured</dt>
              <dd>{formatDateTime(item.capturedAt)}</dd>
            </dl>
          ) : (
            <p className="side-missing">No ledger record</p>
          )}
        </div>
        <div className="break-side">
          <h4>
            Processor settlement
            {item.settlementRows.length > 1 ? ` (${item.settlementRows.length} rows)` : ''}
          </h4>
          {item.settlementRows.length > 0 ? (
            item.settlementRows.map((row) => (
              <dl key={row.networkRef}>
                <dt>Network ref</dt>
                <dd>
                  <button
                    type="button"
                    className="link-button"
                    onClick={() => {
                      logAction('break.openSettlementSource', { networkRef: row.networkRef })
                      onOpenSource({ tab: 'settlement', id: row.networkRef })
                    }}
                  >
                    {row.networkRef}
                  </button>
                </dd>
                <dt>Reference</dt>
                <dd>{row.merchantRef ?? 'blank'}</dd>
                <dt>Settled</dt>
                <dd>{formatMoney(row.settledAmount)}</dd>
                <dt>Fees</dt>
                <dd>
                  {formatMoney(row.interchangeFee)} interchange, {formatMoney(row.processorFee)} processor
                </dd>
                <dt>Settled on</dt>
                <dd>{row.settlementDate}</dd>
              </dl>
            ))
          ) : (
            <p className="side-missing">Never settled</p>
          )}
        </div>
      </div>
    </article>
  )
}
