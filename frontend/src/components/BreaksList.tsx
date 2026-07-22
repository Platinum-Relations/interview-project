import type { BreakItem, Classification, SourceFocus } from '../api/types'
import { BREAK_CATEGORIES, CLASSIFICATION_LABELS } from '../lib/format'
import { logAction } from '../lib/log'
import { BreakCard } from './BreakCard'

interface Props {
  breaks: BreakItem[]
  activeCategory: Classification | null
  onCategoryChange: (category: Classification | null) => void
  onOpenSource: (focus: NonNullable<SourceFocus>) => void
}

export function BreaksList({ breaks, activeCategory, onCategoryChange, onOpenSource }: Props) {
  return (
    <section className="panel" id="section-breaks">
      <div className="panel-heading">
        <h2>Break drill-down</h2>
        <span className="panel-subtitle">
          {activeCategory
            ? `Showing ${CLASSIFICATION_LABELS[activeCategory]} — ${breaks.length} item(s)`
            : `Showing all breaks — ${breaks.length} item(s)`}
        </span>
      </div>
      <div className="filter-row">
        <button
          className={`chip ${activeCategory === null ? 'chip-active' : ''}`}
          onClick={() => {
            logAction('breaks.filter', { category: 'ALL' })
            onCategoryChange(null)
          }}
        >
          All
        </button>
        {BREAK_CATEGORIES.map((category) => (
          <button
            key={category}
            className={`chip ${activeCategory === category ? 'chip-active' : ''}`}
            onClick={() => {
              logAction('breaks.filter', { category })
              onCategoryChange(category)
            }}
          >
            {CLASSIFICATION_LABELS[category]}
          </button>
        ))}
      </div>
      {breaks.length === 0 ? (
        <p className="side-missing">No breaks in this category.</p>
      ) : (
        <div className="break-list">
          {breaks.map((item) => (
            <BreakCard key={item.id} item={item} onOpenSource={onOpenSource} />
          ))}
        </div>
      )}
    </section>
  )
}
