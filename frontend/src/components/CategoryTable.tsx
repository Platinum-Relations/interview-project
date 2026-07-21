import type { CategorySummary, Classification } from '../api/types'
import { CLASSIFICATION_LABELS, formatMoney } from '../lib/format'
import { logAction } from '../lib/log'

interface Props {
  categories: CategorySummary[]
  onSelectCategory: (category: Classification) => void
}

export function CategoryTable({ categories, onSelectCategory }: Props) {
  const nonEmpty = categories.filter((c) => c.count > 0)
  return (
    <section className="panel">
      <h2>Reconciliation summary</h2>
      <table>
        <thead>
          <tr>
            <th>Outcome</th>
            <th className="numeric">Count</th>
            <th className="numeric">Total amount</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {nonEmpty.map((category) => (
            <tr key={category.classification} className={category.classification === 'CLEAN_MATCH' ? 'row-clean' : ''}>
              <td>{CLASSIFICATION_LABELS[category.classification]}</td>
              <td className="numeric">{category.count}</td>
              <td className="numeric">{formatMoney(category.totalAmount)}</td>
              <td className="actions">
                {category.classification !== 'CLEAN_MATCH' && (
                  <button
                    className="link-button"
                    onClick={() => {
                      logAction('summary.drillDown', { category: category.classification })
                      onSelectCategory(category.classification)
                    }}
                  >
                    View breaks
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}
