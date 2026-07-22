import { useCallback, useEffect, useMemo, useState } from 'react'
import { fetchBreaks, fetchLatestSummary, fetchMerchants, fetchQuarantine } from './api/client'
import type { BreakItem, Classification, MerchantRollup, QuarantineRow, RunSummary, SourceFocus } from './api/types'
import { BreaksList } from './components/BreaksList'
import { CategoryTable } from './components/CategoryTable'
import { DevToolsMenu } from './components/DevToolsMenu'
import { ImportPanel } from './components/ImportPanel'
import { MerchantTable } from './components/MerchantTable'
import { QuarantineTable } from './components/QuarantineTable'
import { RawDataPanel } from './components/RawDataPanel'
import { SectionRail, type NavSection } from './components/SectionRail'
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
  const [showSourceData, setShowSourceData] = useState(false)
  const [sourceFocus, setSourceFocus] = useState<SourceFocus>(null)

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
      } else {
        setBreaks([])
        setMerchants([])
        setQuarantine([])
        setShowSourceData(false)
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
      setShowSourceData(false)
      setSourceFocus(null)
      void loadRun(null)
    },
    [loadRun],
  )

  const handleReset = useCallback(() => {
    logAction('app.reset')
    setSummary(null)
    setBreaks([])
    setMerchants([])
    setQuarantine([])
    setCategory(null)
    setShowSourceData(false)
    setSourceFocus(null)
    void loadRun(null)
  }, [loadRun])

  const handleCategoryChange = useCallback(
    (next: Classification | null, options?: { scroll?: boolean }) => {
      logAction('app.filterBreaks', { category: next, scroll: Boolean(options?.scroll) })
      setCategory(next)
      if (summary) {
        void fetchBreaks(summary.runId, next)
          .then((items) => {
            setBreaks(items ?? [])
            logAction('app.filterBreaks.loaded', { category: next, count: items?.length ?? 0 })
          })
          .catch((e) => logError('app.filterBreaks', e))
      }
      if (options?.scroll) {
        window.setTimeout(() => {
          const el = document.getElementById('section-breaks')
          logAction('app.scrollToBreaks', { found: Boolean(el) })
          el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
        }, 50)
      }
    },
    [summary],
  )

  const openSourceData = useCallback((focus: SourceFocus = null) => {
    logAction('app.openSourceData', { focus: focus ? JSON.stringify(focus) : null })
    setShowSourceData(true)
    setSourceFocus(focus)
    window.setTimeout(() => {
      const el = document.getElementById('section-source')
      logAction('app.scrollToSourceData', { found: Boolean(el) })
      el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }, 50)
  }, [])

  const handleFocusHandled = useCallback(() => {
    setSourceFocus(null)
  }, [])

  const navSections: NavSection[] = useMemo(() => {
    const sections: NavSection[] = [{ id: 'section-import', label: 'Import files' }]
    if (!summary) return sections
    sections.push(
      { id: 'section-summary', label: 'Run summary' },
      {
        id: 'section-source',
        label: 'Source data',
        ensureVisible: () => {
          if (!showSourceData) {
            logAction('nav.ensureSourceData')
            setShowSourceData(true)
          }
        },
      },
      { id: 'section-outcomes', label: 'Outcomes' },
      { id: 'section-breaks', label: 'Break drill-down' },
      { id: 'section-merchants', label: 'Per-merchant' },
    )
    if (quarantine.length > 0) {
      sections.push({ id: 'section-quarantine', label: 'Quarantine' })
    }
    return sections
  }, [summary, showSourceData, quarantine.length])

  return (
    <div className="app">
      <SectionRail sections={navSections} />

      <header className="app-header">
        <div>
          <h1>Settlement Reconciliation</h1>
          <p>Internal ledger vs. processor settlement</p>
        </div>
        <DevToolsMenu onReset={handleReset} />
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
          <SummaryCards summary={summary} onOpenSourceData={() => openSourceData(null)} />
          {showSourceData && (
            <RawDataPanel
              runId={summary.runId}
              quarantine={quarantine}
              focus={sourceFocus}
              onFocusHandled={handleFocusHandled}
            />
          )}
          <CategoryTable
            categories={summary.categories}
            onSelectCategory={(c) => handleCategoryChange(c, { scroll: true })}
          />
          <BreaksList
            breaks={breaks}
            activeCategory={category}
            onCategoryChange={(c) => handleCategoryChange(c)}
            onOpenSource={openSourceData}
          />
          <MerchantTable merchants={merchants} />
          <QuarantineTable rows={quarantine} onOpenSource={openSourceData} />
        </>
      )}
    </div>
  )
}
