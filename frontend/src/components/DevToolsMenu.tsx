import { useState } from 'react'
import { deleteAllRuns } from '../api/client'
import { logAction, logError } from '../lib/log'

interface Props {
  onReset: () => void
}

/** Small dev-only helper for exercising the import flow from a clean slate. */
export function DevToolsMenu({ onReset }: Props) {
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)

  async function handleReset() {
    logAction('devtools.resetClicked')
    if (!window.confirm('Delete ALL runs, results, and quarantined rows? This cannot be undone.')) {
      logAction('devtools.resetCancelled')
      return
    }
    setBusy(true)
    try {
      await deleteAllRuns()
      logAction('devtools.resetCompleted')
      setOpen(false)
      onReset()
    } catch (e) {
      logError('devtools.reset', e)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="devtools">
      <button
        className="devtools-toggle"
        onClick={() => {
          logAction('devtools.toggle', { open: !open })
          setOpen(!open)
        }}
      >
        Dev tools
      </button>
      {open && (
        <div className="devtools-menu">
          <p className="devtools-hint">Testing helpers - not part of the reconciliation product.</p>
          <button className="devtools-danger" onClick={handleReset} disabled={busy}>
            {busy ? 'Deleting…' : 'Delete all data'}
          </button>
        </div>
      )}
    </div>
  )
}
