import { Fragment, useEffect, useRef, useState } from 'react'
import { fetchLedgerSource, fetchSettlementSource } from '../api/client'
import type { LedgerSourceRow, QuarantineRow, SettlementSourceRow, SourceFocus } from '../api/types'
import { CLASSIFICATION_LABELS, formatMoney } from '../lib/format'
import { logAction, logError } from '../lib/log'

type Tab = 'ledger' | 'settlement' | 'quarantine'

interface Props {
  runId: number
  quarantine: QuarantineRow[]
  focus: SourceFocus
  onFocusHandled: () => void
}

export function RawDataPanel({ runId, quarantine, focus, onFocusHandled }: Props) {
  const [tab, setTab] = useState<Tab>('ledger')
  const [ledger, setLedger] = useState<LedgerSourceRow[]>([])
  const [settlements, setSettlements] = useState<SettlementSourceRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [highlightId, setHighlightId] = useState<string | null>(null)
  const [expandedId, setExpandedId] = useState<string | null>(null)
  const panelRef = useRef<HTMLElement>(null)
  const highlightRef = useRef<HTMLElement | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    setExpandedId(null)
    Promise.all([fetchLedgerSource(runId), fetchSettlementSource(runId)])
      .then(([ledgerRows, settlementRows]) => {
        if (cancelled) return
        setLedger(ledgerRows ?? [])
        setSettlements(settlementRows ?? [])
        logAction('rawData.loaded', {
          runId,
          ledgerCount: ledgerRows?.length ?? 0,
          settlementCount: settlementRows?.length ?? 0,
        })
      })
      .catch((e) => {
        if (cancelled) return
        logError('rawData.load', e)
        setError('Could not load source rows')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [runId])

  useEffect(() => {
    if (!focus) return
    setTab(focus.tab)
    setHighlightId(focus.id)
    setExpandedId(focus.id)
    logAction('rawData.focus', { tab: focus.tab, id: focus.id })
    panelRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    onFocusHandled()
  }, [focus, onFocusHandled])

  useEffect(() => {
    if (!highlightId) return
    highlightRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }, [highlightId, tab, loading, expandedId])

  function selectTab(next: Tab) {
    logAction('rawData.tab', { tab: next })
    setTab(next)
    setHighlightId(null)
    setExpandedId(null)
  }

  function toggleExpand(id: string) {
    const next = expandedId === id ? null : id
    logAction('rawData.toggleExpand', { id, expanded: next !== null, tab })
    setExpandedId(next)
  }

  return (
    <section className="panel raw-data-panel" ref={panelRef} id="section-source">
      <div className="panel-heading">
        <h2>Source data</h2>
        <span className="panel-subtitle">
          Click a row to see what it paired with and which match rule was used. Badge shows the rule
          without expanding.
        </span>
      </div>

      <div className="filter-row">
        <button className={`chip ${tab === 'ledger' ? 'chip-active' : ''}`} onClick={() => selectTab('ledger')}>
          Ledger CSV ({ledger.length})
        </button>
        <button
          className={`chip ${tab === 'settlement' ? 'chip-active' : ''}`}
          onClick={() => selectTab('settlement')}
        >
          Settlement JSON ({settlements.length})
        </button>
        <button
          className={`chip ${tab === 'quarantine' ? 'chip-active' : ''}`}
          onClick={() => selectTab('quarantine')}
        >
          Quarantine ({quarantine.length})
        </button>
      </div>

      {loading && <p className="message">Loading source rows…</p>}
      {error && <p className="message message-error">{error}</p>}

      {!loading && !error && tab === 'ledger' && (
        <div className="raw-table-wrap">
          <table className="raw-table">
            <thead>
              <tr>
                <th>internal_txn_id</th>
                <th>merchant_id</th>
                <th>merchant_ref</th>
                <th>card_type</th>
                <th>card_last4</th>
                <th className="numeric">gross_amount</th>
                <th>currency</th>
                <th>type</th>
                <th>captured_at</th>
                <th>match</th>
                <th>outcome</th>
              </tr>
            </thead>
            <tbody>
              {ledger.map((row) => {
                const active = highlightId === row.internal_txn_id
                const expanded = expandedId === row.internal_txn_id
                return (
                  <Fragment key={row.internal_txn_id}>
                    <tr
                      ref={active ? (el) => { highlightRef.current = el } : undefined}
                      className={`raw-row-clickable ${active ? 'row-highlight' : ''} ${expanded ? 'row-expanded' : ''}`}
                      onClick={() => toggleExpand(row.internal_txn_id)}
                    >
                      <td>{row.internal_txn_id}</td>
                      <td>{row.merchant_id}</td>
                      <td>{row.merchant_ref ?? ''}</td>
                      <td>{row.card_type ?? ''}</td>
                      <td>{row.card_last4 ?? ''}</td>
                      <td className="numeric">{row.gross_amount}</td>
                      <td>{row.currency}</td>
                      <td>{row.type ?? ''}</td>
                      <td>{row.captured_at ?? ''}</td>
                      <td>
                        <MatchBadge method={row.match_method} label={row.match_method_label} />
                      </td>
                      <td>{CLASSIFICATION_LABELS[row.classification]}</td>
                    </tr>
                    {expanded && (
                      <tr className="raw-expand-row">
                        <td colSpan={11}>
                          <PairDetail
                            reason={row.reason ?? 'No reason stored for this run — wipe and re-import after restarting the backend.'}
                            matchMethod={row.match_method ?? 'UNMATCHED'}
                            matchLabel={row.match_method_label ?? 'unmatched'}
                            ledger={row}
                            settlements={row.paired_settlements ?? []}
                          />
                        </td>
                      </tr>
                    )}
                  </Fragment>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {!loading && !error && tab === 'settlement' && (
        <div className="raw-table-wrap">
          <table className="raw-table">
            <thead>
              <tr>
                <th>network_ref</th>
                <th>merchant_ref</th>
                <th>merchant_id</th>
                <th>card_last4</th>
                <th>card_type</th>
                <th className="numeric">settled_amount</th>
                <th className="numeric">interchange_fee</th>
                <th className="numeric">processor_fee</th>
                <th>currency</th>
                <th>settlement_date</th>
                <th>match</th>
                <th>outcome</th>
              </tr>
            </thead>
            <tbody>
              {settlements.map((row) => {
                const active = highlightId === row.network_ref
                const expanded = expandedId === row.network_ref
                return (
                  <Fragment key={row.network_ref}>
                    <tr
                      ref={active ? (el) => { highlightRef.current = el } : undefined}
                      className={`raw-row-clickable ${active ? 'row-highlight' : ''} ${expanded ? 'row-expanded' : ''}`}
                      onClick={() => toggleExpand(row.network_ref)}
                    >
                      <td>{row.network_ref}</td>
                      <td>{row.merchant_ref ?? ''}</td>
                      <td>{row.merchant_id}</td>
                      <td>{row.card_last4 ?? ''}</td>
                      <td>{row.card_type ?? ''}</td>
                      <td className="numeric">{row.settled_amount}</td>
                      <td className="numeric">{row.interchange_fee}</td>
                      <td className="numeric">{row.processor_fee}</td>
                      <td>{row.currency}</td>
                      <td>{row.settlement_date ?? ''}</td>
                      <td>
                        <MatchBadge method={row.match_method} label={row.match_method_label} />
                      </td>
                      <td>{CLASSIFICATION_LABELS[row.classification]}</td>
                    </tr>
                    {expanded && (
                      <tr className="raw-expand-row">
                        <td colSpan={12}>
                          <PairDetail
                            reason={row.reason ?? 'No reason stored for this run — wipe and re-import after restarting the backend.'}
                            matchMethod={row.match_method ?? 'UNMATCHED'}
                            matchLabel={row.match_method_label ?? 'unmatched'}
                            ledger={row.paired_ledger}
                            settlements={[row]}
                          />
                        </td>
                      </tr>
                    )}
                  </Fragment>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {!loading && !error && tab === 'quarantine' && (
        <div className="quarantine-raw-list">
          {quarantine.length === 0 ? (
            <p className="side-missing">No quarantined rows for this run.</p>
          ) : (
            quarantine.map((row) => {
              const active = highlightId === row.rowIdentifier
              return (
                <article
                  key={`${row.source}-${row.rowIdentifier}`}
                  className={`quarantine-raw-card ${active ? 'row-highlight' : ''}`}
                >
                  <header>
                    <strong>{row.rowIdentifier}</strong>
                    <span>
                      {row.source === 'INTERNAL' ? 'Ledger CSV' : 'Settlement JSON'} — {row.reasons}
                    </span>
                  </header>
                  <pre ref={active ? (el) => { highlightRef.current = el } : undefined}>{row.rawContent}</pre>
                </article>
              )
            })
          )}
        </div>
      )}
    </section>
  )
}

function MatchBadge({ method, label }: { method?: string | null; label?: string | null }) {
  const safeMethod = (method ?? 'UNMATCHED').toLowerCase()
  const safeLabel = label ?? 'unmatched'
  return <span className={`match-badge match-${safeMethod}`}>{safeLabel}</span>
}

function PairDetail({
  reason,
  matchMethod,
  matchLabel,
  ledger,
  settlements,
}: {
  reason: string
  matchMethod: string
  matchLabel: string
  ledger: LedgerSourceRow | null
  settlements: SettlementSourceRow[]
}) {
  return (
    <div className="pair-detail">
      <p className="pair-detail-meta">
        <MatchBadge method={matchMethod} label={matchLabel} />
        <span>{reason}</span>
      </p>
      <div className="break-sides">
        <div className="break-side">
          <h4>Internal ledger</h4>
          {ledger ? (
            <dl>
              <dt>Transaction</dt>
              <dd>{ledger.internal_txn_id}</dd>
              <dt>Reference</dt>
              <dd>{ledger.merchant_ref ?? '-'}</dd>
              <dt>Merchant</dt>
              <dd>{ledger.merchant_id}</dd>
              <dt>Card</dt>
              <dd>
                {ledger.card_type ?? '-'} ****{ledger.card_last4 ?? ''}
              </dd>
              <dt>Type</dt>
              <dd>{ledger.type}</dd>
              <dt>Gross</dt>
              <dd>{formatMoney(ledger.gross_amount)}</dd>
              <dt>Captured</dt>
              <dd>{ledger.captured_at ?? '-'}</dd>
            </dl>
          ) : (
            <p className="side-missing">No ledger record</p>
          )}
        </div>
        <div className="break-side">
          <h4>
            Processor settlement
            {settlements.length > 1 ? ` (${settlements.length} rows)` : ''}
          </h4>
          {settlements.length > 0 ? (
            settlements.map((s) => (
              <dl key={s.network_ref}>
                <dt>Network ref</dt>
                <dd>{s.network_ref}</dd>
                <dt>Reference</dt>
                <dd>{s.merchant_ref ?? 'blank'}</dd>
                <dt>Merchant</dt>
                <dd>{s.merchant_id}</dd>
                <dt>Settled</dt>
                <dd>{formatMoney(s.settled_amount)}</dd>
                <dt>Fees</dt>
                <dd>
                  {formatMoney(s.interchange_fee)} interchange, {formatMoney(s.processor_fee)} processor
                </dd>
                <dt>Settled on</dt>
                <dd>{s.settlement_date ?? '-'}</dd>
              </dl>
            ))
          ) : (
            <p className="side-missing">Never settled</p>
          )}
        </div>
      </div>
    </div>
  )
}
