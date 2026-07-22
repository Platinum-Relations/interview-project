import { useEffect, useRef, useState } from 'react'
import { logAction } from '../lib/log'

export interface NavSection {
  id: string
  label: string
  /** If true, the section may not be in the DOM until opened (e.g. source data). */
  ensureVisible?: () => void
}

interface Props {
  sections: NavSection[]
}

/**
 * ChatGPT-style right rail: collapsed markers, expands on hover with labels,
 * click scrolls to the section. Tracks the active section while scrolling,
 * but ignores intermediate sections while a click-driven scroll is in flight.
 */
export function SectionRail({ sections }: Props) {
  const [expanded, setExpanded] = useState(false)
  const [activeId, setActiveId] = useState<string | null>(sections[0]?.id ?? null)
  const scrollLockUntilRef = useRef(0)
  const scrollEndTimerRef = useRef<number | null>(null)

  useEffect(() => {
    const ids = sections.map((s) => s.id)
    const elements = ids
      .map((id) => document.getElementById(id))
      .filter((el): el is HTMLElement => el != null)

    if (elements.length === 0) return

    const observer = new IntersectionObserver(
      (entries) => {
        // While navigating from a click, don't let pass-through sections steal active.
        if (Date.now() < scrollLockUntilRef.current) return

        const visible = entries
          .filter((e) => e.isIntersecting)
          .sort((a, b) => b.intersectionRatio - a.intersectionRatio)
        if (visible[0]?.target.id) {
          setActiveId(visible[0].target.id)
        }
      },
      { rootMargin: '-20% 0px -55% 0px', threshold: [0.1, 0.25, 0.5] },
    )

    elements.forEach((el) => observer.observe(el))
    return () => observer.disconnect()
  }, [sections])

  useEffect(() => {
    return () => {
      if (scrollEndTimerRef.current != null) {
        window.clearTimeout(scrollEndTimerRef.current)
      }
    }
  }, [])

  function goTo(section: NavSection) {
    logAction('nav.sectionClick', { id: section.id, label: section.label })
    setActiveId(section.id)
    // Lock active state for the duration of the smooth scroll (~1s is enough for long pages).
    scrollLockUntilRef.current = Date.now() + 1200
    if (scrollEndTimerRef.current != null) {
      window.clearTimeout(scrollEndTimerRef.current)
    }
    scrollEndTimerRef.current = window.setTimeout(() => {
      scrollLockUntilRef.current = 0
      logAction('nav.scrollLockReleased', { id: section.id })
    }, 1200)

    section.ensureVisible?.()
    window.setTimeout(() => {
      const el = document.getElementById(section.id)
      logAction('nav.sectionScroll', { id: section.id, found: Boolean(el) })
      el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }, section.ensureVisible ? 60 : 0)
  }

  if (sections.length === 0) return null

  return (
    <nav
      className={`section-rail ${expanded ? 'section-rail-expanded' : ''}`}
      aria-label="Page sections"
      onMouseEnter={() => {
        logAction('nav.railExpand')
        setExpanded(true)
      }}
      onMouseLeave={() => {
        logAction('nav.railCollapse')
        setExpanded(false)
      }}
    >
      <div className="section-rail-inner">
        <p className="section-rail-title">{expanded ? 'On this page' : 'Nav'}</p>
        <ul className="section-rail-list">
          {sections.map((section) => (
            <li key={section.id}>
              <button
                type="button"
                className={`section-rail-item ${activeId === section.id ? 'section-rail-item-active' : ''}`}
                onClick={() => goTo(section)}
                title={section.label}
              >
                <span className="section-rail-dot" aria-hidden="true" />
                <span className="section-rail-label">{section.label}</span>
              </button>
            </li>
          ))}
        </ul>
      </div>
    </nav>
  )
}
