import { useCallback, useEffect, useState } from 'react'
import { fetchBreaks, fetchLatestSummary, fetchMerchants, fetchQuarantine } from './api/client'
import type { BreakItem, Classification, MerchantRollup, QuarantineRow, RunSummary } from './api/types'
import { BreaksList } from './components/BreaksList'
import { CategoryTable } from './components/CategoryTable'
import { ImportPanel } from './components/ImportPanel'
import { MerchantTable } from './components/MerchantTable'
import { QuarantineTable } from './components/QuarantineTable'
import { SummaryCards } from './components/SummaryCards'
import { logAction, logError } from './lib/log'

export default function App() {
  const [summary, setSummary] = useState<RunSummary | null>(null)
  const [breaks, setBreaks] = useState<BreakItem[]>([])
  const [merchants, setMerchants] = useState<MerchantRollup[]>([])
  const [quarantine, setQuarantine] = useState<QuarantineRow[]>([])
  const [category, setCategory] = useState<Classification | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const loadRun = useCallback(async (activeCategory: Classification | null) => {
    setLoading(true)
    setLoadError(null)
    try {
      const latest = await fetchLatestSummary()
      setSummary(latest)
      if (latest) {
        const [breakItems, merchantRollups, quarantineRows] = await Promise.all([
          fetchBreaks(latest.runId, activeCategory),
          fetchMerchants(latest.runId),
          fetchQuarantine(latest.runId),
        ])
        setBreaks(breakItems ?? [])
        setMerchants(merchantRollups ?? [])
        setQuarantine(quarantineRows ?? [])
      }
    } catch (e) {
      logError('app.loadRun', e)
      setLoadError('Could not reach the backend. Is it running on port 8080?')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    logAction('app.start')
    void loadRun(null)
  }, [loadRun])

  const handleImported = useCallback(
    (runId: number, alreadyImported: boolean) => {
      logAction('app.imported', { runId, alreadyImported })
      setCategory(null)
      void loadRun(null)
    },
    [loadRun],
  )

  const handleCategoryChange = useCallback(
    (next: Classification | null) => {
      setCategory(next)
      if (summary) {
        void fetchBreaks(summary.runId, next).then((items) => setBreaks(items ?? []))
      }
    },
    [summary],
  )

  return (
    <div className="app">
      <header className="app-header">
        <h1>Settlement Reconciliation</h1>
        <p>Internal ledger vs. processor settlement</p>
      </header>

      <ImportPanel onImported={handleImported} />

      {loadError && <p className="message message-error">{loadError}</p>}
      {loading && <p className="message">Loading…</p>}

      {!loading && !summary && !loadError && (
        <section className="panel">
          <h2>No runs yet</h2>
          <p>
            Import the internal transactions CSV and the processor settlement JSON above to run the
            first reconciliation.
          </p>
        </section>
      )}

      {summary && (
        <>
          <SummaryCards summary={summary} />
          <CategoryTable categories={summary.categories} onSelectCategory={handleCategoryChange} />
          <MerchantTable merchants={merchants} />
          <BreaksList breaks={breaks} activeCategory={category} onCategoryChange={handleCategoryChange} />
          <QuarantineTable rows={quarantine} />
        </>
      )}
    </div>
  )
}
