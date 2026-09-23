import { useEffect, useRef, useState } from 'react'
import type { FocusEvent, KeyboardEvent } from 'react'
import type { StopSuggestion } from '../types'
import { api, friendlyMessage } from '../api'

type SuggestionState = 'idle' | 'loading' | 'ok' | 'empty' | 'error'

/** Trim → collapse whitespace → lowercase; mirrors the backend normalization. */
const norm = (s: string) => s.trim().replace(/\s+/g, ' ').toLowerCase()

export default function SearchPanel({
  initialFrom = '',
  initialTo = '',
  committedFrom = '',
  committedTo = '',
  onDirtyChange,
  onSearch
}: {
  initialFrom?: string
  initialTo?: string
  committedFrom?: string
  committedTo?: string
  onDirtyChange?: (dirty: boolean, fromEmpty: boolean, toEmpty: boolean) => void
  onSearch: (from: string, to: string) => Promise<void>
}) {
  const [from, setFrom] = useState(initialFrom)
  const [to, setTo] = useState(initialTo)
  const [suggestions, setSuggestions] = useState<StopSuggestion[]>([])
  const [sugField, setSugField] = useState<'from' | 'to'>('from')
  const [sugState, setSugState] = useState<SuggestionState>('idle')
  const [termText, setTermText] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [activeIndex, setActiveIndex] = useState(-1)

  const panelRef = useRef<HTMLDivElement | null>(null)
  const seqRef = useRef(0)
  const timerRef = useRef(0)

  // Editing vs committed: whenever the inputs differ from the last committed
  // search, tell the app so it can stop showing the old route/bus as active.
  useEffect(() => {
    const dirty =
      norm(from) !== norm(committedFrom ?? '') || norm(to) !== norm(committedTo ?? '')
    onDirtyChange?.(dirty, !from.trim(), !to.trim())
  }, [from, to, committedFrom, committedTo, onDirtyChange])

  useEffect(() => {
    const term = (sugField === 'from' ? from : to).trim()
    if (term.length < 2) {
      setSuggestions([])
      setSugState('idle')
      setTermText('')
      return
    }
    const seq = ++seqRef.current
    setTermText(term)
    setSugState('loading')
    const t = window.setTimeout(() => {
      setSuggestions([])
      setActiveIndex(-1)
      api
        .stopSuggestions(term)
        .then((list) => {
          if (seqRef.current !== seq) return // stale response
          setSuggestions(list)
          setSugState(list.length ? 'ok' : 'empty')
        })
        .catch(() => {
          if (seqRef.current !== seq) return
          setSuggestions([])
          setSugState('error')
        })
    }, 250)
    timerRef.current = t
    return () => {
      window.clearTimeout(t)
      if (timerRef.current === t) timerRef.current = 0
      ++seqRef.current // invalidate any in-flight request
    }
  }, [from, to, sugField])

  const swap = () => {
    setFrom(to)
    setTo(from)
  }

  const doSearch = async (f?: string, t?: string) => {
    const fromV = (f ?? from).trim()
    const toV = (t ?? to).trim()
    if (!fromV || !toV) return
    setError(null)
    setLoading(true)
    setSuggestions([])
    setSugState('idle')
    try {
      await onSearch(fromV, toV)
    } catch (e) {
      setError(friendlyMessage(e))
    } finally {
      setLoading(false)
    }
  }

  // Fill the active field with the official stop name. Committing still happens
  // through the Search button (or Enter), so typing/selecting never fakes the
  // active search before the user asks.
  const pickSuggestion = (s: StopSuggestion) => {
    if (sugField === 'from') setFrom(s.name)
    else setTo(s.name)
    setSuggestions([])
    setSugState('idle')
  }

  // Drop the dropdown so the header returns to its compact size. Delayed so a
  // click on a suggestion (fires after blur) still lands before we clear.
  const closeSuggestions = (delay: number) => {
    window.clearTimeout(timerRef.current)
    timerRef.current = 0
    window.setTimeout(() => {
      ++seqRef.current
      setSuggestions([])
      setSugState('idle')
      setActiveIndex(-1)
    }, delay)
  }

  const handleBlur = (e: FocusEvent<HTMLInputElement>) => {
    const next = e.relatedTarget as Node | null
    if (next && panelRef.current?.contains(next)) return // focus moved within the panel
    closeSuggestions(140)
  }

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Escape') {
      e.preventDefault()
      closeSuggestions(0)
      return
    }
    if (sugState !== 'ok') return
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setActiveIndex((i) => Math.min(i + 1, suggestions.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setActiveIndex((i) => Math.max(i - 1, -1))
    } else if (e.key === 'Enter' && activeIndex >= 0) {
      e.preventDefault()
      pickSuggestion(suggestions[activeIndex])
    }
  }

  return (
    <div className="search-panel" ref={panelRef}>
      <form
        className="search-row"
        role="search"
        aria-label="Find buses"
        onSubmit={(e) => {
          e.preventDefault()
          void doSearch()
        }}
      >
        <div className="field">
          <span className="field-icon" aria-hidden="true">📍</span>
          <input
            value={from}
            onChange={(e) => setFrom(e.target.value)}
            onFocus={() => setSugField('from')}
            onBlur={handleBlur}
            onKeyDown={onKeyDown}
            placeholder="From stop…"
            aria-label="From stop"
            autoComplete="off"
            enterKeyHint="search"
          />
          {from && (
            <button type="button" className="field-clear" aria-label="Clear from stop" onClick={() => setFrom('')}>✕</button>
          )}
        </div>
        <button type="button" className="swap-btn" aria-label="Swap from and to" title="Swap locations" onClick={swap}>
          ↔
        </button>
        <div className="field">
          <span className="field-icon" aria-hidden="true">📍</span>
          <input
            value={to}
            onChange={(e) => setTo(e.target.value)}
            onFocus={() => setSugField('to')}
            onBlur={handleBlur}
            onKeyDown={onKeyDown}
            placeholder="To stop…"
            aria-label="To stop"
            autoComplete="off"
            enterKeyHint="search"
          />
          {to && (
            <button type="button" className="field-clear" aria-label="Clear to stop" onClick={() => setTo('')}>✕</button>
          )}
        </div>
        <button type="submit" className="search-submit" disabled={loading || !from.trim() || !to.trim()} aria-busy={loading}>
          {loading ? '⟳' : '🔍'}
          <span className="submit-label">Search</span>
        </button>
      </form>

      {error && <p className="form-error" role="alert">⚠️ {error}</p>}

      {sugState === 'loading' && (
        <div className="suggestions sugg-note" role="status">⏳ Loading stops…</div>
      )}
      {sugState === 'error' && (
        <div className="suggestions sugg-note sugg-error" role="alert">⚠️ Could not load suggestions. Check your connection.</div>
      )}
      {sugState === 'empty' && (
        <div className="suggestions sugg-note suger-empty" role="status">No stops match “{termText}”. Try another spelling.</div>
      )}

      {sugState === 'ok' && (
        <ul className="suggestions" role="listbox" aria-label="Stop suggestions">
          {suggestions.map((s, i) => (
            <li key={s.name} role="option" aria-selected={i === activeIndex}>
              <button
                type="button"
                onClick={() => pickSuggestion(s)}
                className={i === activeIndex ? 'sug-active' : ''}
              >
                📍 {s.name}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}