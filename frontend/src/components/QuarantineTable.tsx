import type { QuarantineRow, SourceFocus } from '../api/types'
import { logAction } from '../lib/log'

interface Props {
  rows: QuarantineRow[]
  onOpenSource: (focus: NonNullable<SourceFocus>) => void
}

export function QuarantineTable({ rows, onOpenSource }: Props) {
  if (rows.length === 0) {
    return null
  }
  return (
    <section className="panel" id="section-quarantine">
      <div className="panel-heading">
        <h2>Quarantined rows</h2>
        <span className="panel-subtitle">
          Malformed input excluded from reconciliation - not counted as breaks. Click a row id to see
          the original file text.
        </span>
      </div>
      <table>
        <thead>
          <tr>
            <th>Source</th>
            <th>Row</th>
            <th>Why it was quarantined</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={`${row.source}-${row.rowIdentifier}`}>
              <td>{row.source === 'INTERNAL' ? 'Ledger CSV' : 'Settlement JSON'}</td>
              <td>
                <button
                  type="button"
                  className="link-button"
                  onClick={() => {
                    logAction('quarantine.openRaw', { rowIdentifier: row.rowIdentifier, source: row.source })
                    onOpenSource({ tab: 'quarantine', id: row.rowIdentifier })
                  }}
                >
                  {row.rowIdentifier}
                </button>
              </td>
              <td>{row.reasons}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}
