import type { QuarantineRow } from '../api/types'

interface Props {
  rows: QuarantineRow[]
}

export function QuarantineTable({ rows }: Props) {
  if (rows.length === 0) {
    return null
  }
  return (
    <section className="panel">
      <div className="panel-heading">
        <h2>Quarantined rows</h2>
        <span className="panel-subtitle">
          Malformed input excluded from reconciliation - not counted as breaks
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
              <td>{row.rowIdentifier}</td>
              <td>{row.reasons}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}
