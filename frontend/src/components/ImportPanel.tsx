import { useRef, useState } from 'react'
import { importFiles } from '../api/client'
import { logAction, logError } from '../lib/log'

interface Props {
  onImported: (runId: number, alreadyImported: boolean) => void
}

export function ImportPanel({ onImported }: Props) {
  const internalRef = useRef<HTMLInputElement>(null)
  const settlementRef = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  async function handleImport() {
    const internal = internalRef.current?.files?.[0]
    const settlement = settlementRef.current?.files?.[0]
    logAction('import.submit', {
      internalFile: internal?.name ?? null,
      settlementFile: settlement?.name ?? null,
    })

    if (!internal || !settlement) {
      setError('Select both files: the internal transactions CSV and the processor settlement JSON.')
      return
    }

    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      const result = await importFiles(internal, settlement)
      setNotice(
        result.alreadyImported
          ? `These exact files were already imported as run ${result.runId}; showing the existing results.`
          : `Imported and reconciled as run ${result.runId}.`,
      )
      onImported(result.runId, result.alreadyImported)
    } catch (e) {
      logError('import.submit', e)
      setError(e instanceof Error ? e.message : 'Import failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="panel import-panel">
      <h2>Import files</h2>
      <div className="import-controls">
        <label className="file-field">
          <span>Internal ledger (CSV)</span>
          <input
            ref={internalRef}
            type="file"
            accept=".csv"
            onChange={(e) => logAction('import.selectInternalFile', { file: e.target.files?.[0]?.name ?? null })}
          />
        </label>
        <label className="file-field">
          <span>Processor settlement (JSON)</span>
          <input
            ref={settlementRef}
            type="file"
            accept=".json"
            onChange={(e) => logAction('import.selectSettlementFile', { file: e.target.files?.[0]?.name ?? null })}
          />
        </label>
        <button onClick={handleImport} disabled={busy}>
          {busy ? 'Reconciling…' : 'Import and reconcile'}
        </button>
      </div>
      {error && <p className="message message-error">{error}</p>}
      {notice && <p className="message message-ok">{notice}</p>}
    </section>
  )
}
