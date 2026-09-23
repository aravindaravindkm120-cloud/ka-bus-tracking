import { useEffect, useRef, useState } from 'react'
import type { TouchEvent } from 'react'
import type { BusSummary, FavoriteItem, RecentSearchItem } from '../types'
import { api, friendlyMessage } from '../api'

const statusEmoji = (s: string) =>
  s.toLowerCase() === 'live' ? '🟢' : s.toLowerCase() === 'stale' ? '🟡' : '⚫'

const timeAgo = (iso: string | null | undefined) => {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const secs = Math.max(0, Math.round((Date.now() - d.getTime()) / 1000))
  if (secs < 10) return 'just now'
  if (secs < 60) return `${secs}s ago`
  const mins = Math.round(secs / 60)
  if (mins < 60) return `${mins}m ago`
  return `${Math.round(mins / 60)}h ago`
}

const PULL_THRESHOLD = 56
const SKELETON_ROWS = [0, 1, 2, 3, 4]

export default function ListPanel({
  onSelect,
  onRefresh,
  onRecent,
  onFavorite,
  from,
  to,
  buses,
  notice,
  loading,
  liveError
}: {
  onSelect: (b: BusSummary) => void
  onRefresh: () => Promise<boolean>
  onRecent: (from: string, to: string) => void
  onFavorite: (label: string) => void
  from: string
  to: string
  buses: BusSummary[]
  notice?: string | null
  loading?: boolean
  liveError?: string | null
}) {
  const [tab, setTab] = useState<'results' | 'favorites' | 'recent'>('results')
  const [favorites, setFavorites] = useState<FavoriteItem[]>([])
  const [recent, setRecent] = useState<RecentSearchItem[]>([])
  const [favLoading, setFavLoading] = useState(false)
  const [recLoading, setRecLoading] = useState(false)
  const [favError, setFavError] = useState<string | null>(null)
  const [recError, setRecError] = useState<string | null>(null)
  const [refreshMsg, setRefreshMsg] = useState<string | null>(null)

  const [pull, setPull] = useState(0)
  const [refreshing, setRefreshing] = useState(false)
  const pullStart = useRef<number | null>(null)
  const bodyRef = useRef<HTMLDivElement | null>(null)
  const refreshMsgTimer = useRef(0)

  const flashMsg = (msg: string) => {
    setRefreshMsg(msg)
    window.clearTimeout(refreshMsgTimer.current)
    refreshMsgTimer.current = window.setTimeout(() => setRefreshMsg(null), 4000)
  }

  useEffect(() => () => window.clearTimeout(refreshMsgTimer.current), [])

  const loadFavorites = async (quiet = false) => {
    if (!quiet) setFavLoading(true)
    setFavError(null)
    try {
      setFavorites(await api.favorites())
    } catch (e) {
      setFavError(friendlyMessage(e))
    } finally {
      if (!quiet) setFavLoading(false)
    }
  }

  const loadRecent = async (quiet = false) => {
    if (!quiet) setRecLoading(true)
    setRecError(null)
    try {
      setRecent(await api.recentSearches())
    } catch (e) {
      setRecError(friendlyMessage(e))
    } finally {
      if (!quiet) setRecLoading(false)
    }
  }

  useEffect(() => {
    if (tab === 'favorites') void loadFavorites()
  }, [tab])

  useEffect(() => {
    if (tab === 'recent') void loadRecent()
  }, [tab])

  const removeFav = async (f: FavoriteItem) => {
    try {
      await api.removeFavorite(f.itemType, f.itemId)
      setFavorites((prev) => prev.filter((x) => x.favoriteId !== f.favoriteId))
      setFavError(null)
    } catch (e) {
      setFavError(friendlyMessage(e))
    }
  }

  const doRefresh = async () => {
    if (refreshing) return
    setRefreshing(true)
    setRefreshMsg(null)
    try {
      const ok = await onRefresh()
      if (tab === 'favorites') await loadFavorites(true)
      if (tab === 'recent') await loadRecent(true)
      if (!ok) flashMsg('📡 Could not refresh. Please check your connection and try again.')
    } catch {
      flashMsg('📡 Could not refresh. Please check your connection and try again.')
    } finally {
      setRefreshing(false)
      setPull(0)
    }
  }

  const onTouchStart = (e: TouchEvent<HTMLDivElement>) => {
    if (refreshing || !bodyRef.current) {
      pullStart.current = null
      return
    }
    // Only start a pull gesture when the list is scrolled to the very top,
    // so scrolling inside the list never triggers a refresh accidentally.
    if (bodyRef.current.scrollTop <= 0) pullStart.current = e.touches[0].clientY
    else pullStart.current = null
  }

  const onTouchMove = (e: TouchEvent<HTMLDivElement>) => {
    if (pullStart.current == null || refreshing) return
    const dy = e.touches[0].clientY - pullStart.current
    if (dy > 0) setPull(Math.min(dy, 84))
  }

  const onTouchEnd = () => {
    if (pullStart.current == null) return
    pullStart.current = null
    if (pull >= PULL_THRESHOLD) {
      setPull(0)
      void doRefresh()
    } else {
      setPull(0)
    }
  }

  const resultsContent = () => {
    if (loading) {
      return (
        <ul className="bus-list" aria-label="Loading buses">
          {SKELETON_ROWS.map((i) => (
            <li key={i} className="skeleton-row">
              <div className="skeleton w-60" />
              <div className="skeleton w-85" />
            </li>
          ))}
        </ul>
      )
    }
    if (liveError && buses.length === 0) {
      return (
        <ul className="bus-list">
          <li className="empty error">
            <span role="alert">⚠️ {liveError}</span>
            <button className="retry-btn" onClick={() => void onRefresh()}>🔁 Retry</button>
          </li>
        </ul>
      )
    }
    if (buses.length === 0 && !refreshing) {
      return (
        <ul className="bus-list">
          <li className="empty">
            {from && to ? (
              <>🔍 No buses found between {from} and {to}.</>
            ) : (
              <>🚌 No live buses right now.{liveError ? ' Please check your connection.' : ''}</>
            )}
          </li>
        </ul>
      )
    }
    return (
      <ul className="bus-list">
        {buses.map((b) => (
          <li key={b.busId}>
            <button onClick={() => onSelect(b)} aria-label={`Open bus ${b.registrationNo}`}>
              <div className="bus-line">
                <strong>🚌 {b.registrationNo}</strong>
                <span className={`status-chip status-${b.status.toLowerCase()}`}>{statusEmoji(b.status)} {b.status}</span>
              </div>
              <div className="bus-meta">
                🛣️ {b.routeName ?? 'No live trip'}
                {b.speedKmh != null && ` · 💨 ${b.speedKmh} km/h`}
                {b.origin && b.destination && ` · 📍 ${b.origin} → ${b.destination}`}
                {b.lastUpdate && ` · ⏱ ${timeAgo(b.lastUpdate)}`}
              </div>
            </button>
          </li>
        ))}
      </ul>
    )
  }

  return (
    <div className="list-panel">
      <div className="tabs" role="tablist" aria-label="Bus list">
        <button
          className={tab === 'results' ? 'active' : ''}
          onClick={() => setTab('results')}
          role="tab"
          aria-selected={tab === 'results'}
        >
          🚌 {from && to ? `${from} → ${to}` : 'Live buses'}
          {buses.length > 0 && <span className="count">{buses.length}</span>}
        </button>
        <button
          className={tab === 'favorites' ? 'active' : ''}
          onClick={() => setTab('favorites')}
          role="tab"
          aria-selected={tab === 'favorites'}
        >
          ⭐ Saved
        </button>
        <button
          className={tab === 'recent' ? 'active' : ''}
          onClick={() => setTab('recent')}
          role="tab"
          aria-selected={tab === 'recent'}
        >
          🕓 Recent
        </button>
        <button className="tab-side" onClick={() => void doRefresh()} aria-label="Refresh current tab" title="Refresh">
          ⟳
        </button>
      </div>

      <div
        className="pull-zone"
        onTouchStart={onTouchStart}
        onTouchMove={onTouchMove}
        onTouchEnd={onTouchEnd}
        onTouchCancel={onTouchEnd}
      >
        {(pull > 0 || refreshing) && (
          <div className="pull-indicator" style={{ height: refreshing ? 30 : Math.min(pull, 42) }} role="status">
            {refreshing ? '⟳ Refreshing…' : pull >= PULL_THRESHOLD ? '🔄 Release to refresh' : '🔄 Pull to refresh'}
          </div>
        )}

        {notice && tab === 'results' && <p className="notice" role="status">🔔 {notice}</p>}
        {refreshMsg && tab === 'results' && <p className="notice notice-error" role="alert">⚠️ {refreshMsg}</p>}

        <div className="list-body" ref={bodyRef}>
          {tab === 'results' && resultsContent()}

          {tab === 'favorites' && (
            <ul className="bus-list">
              {favLoading && <li className="empty">🔎 Loading saved routes…</li>}
              {!favLoading && favorites.length === 0 && !favError && (
                <li className="empty">
                  <span>⭐ No favorite routes yet. Open a bus and tap “Save Route”.</span>
                  <button className="retry-btn" onClick={() => void loadFavorites()}>🔁 Refresh</button>
                </li>
              )}
              {favError && !favLoading && (
                <li className="empty error">
                  <span role="alert">⚠️ {favError}</span>
                  <button className="retry-btn" onClick={() => void loadFavorites()}>🔁 Retry</button>
                </li>
              )}
              {favorites.map((f) => (
                <li key={f.favoriteId}>
                  <button onClick={() => onFavorite(f.label)} aria-label={`Search ${f.label}`}>
                    <div className="bus-line">
                      <strong>⭐ {f.label}</strong>
                      <span className="fav-type">🛣️ Saved route</span>
                    </div>
                    <div className="bus-meta">🔍 Tap to search buses on this route</div>
                  </button>
                  <button className="remove" onClick={() => void removeFav(f)} aria-label={`Remove ${f.label}`} title="Remove">
                    ❌
                  </button>
                </li>
              ))}
            </ul>
          )}

          {tab === 'recent' && (
            <ul className="bus-list">
              {recLoading && <li className="empty">🔎 Loading recent searches…</li>}
              {!recLoading && recent.length === 0 && !recError && (
                <li className="empty">🕓 No recent searches. Search for buses to see them here.</li>
              )}
              {recError && !recLoading && (
                <li className="empty error">
                  <span role="alert">⚠️ {recError}</span>
                  <button className="retry-btn" onClick={() => void loadRecent()}>🔁 Retry</button>
                </li>
              )}
              {recent.map((r, i) => (
                <li key={`${r.from}-${r.to}-${i}`}>
                  <button onClick={() => onRecent(r.from, r.to)} aria-label={`Search from ${r.from} to ${r.to}`}>
                    <div className="bus-line">
                      <strong>🔍 {r.from} → {r.to}</strong>
                    </div>
                    <div className="bus-meta">🕓 {new Date(r.searchedAt).toLocaleString()}</div>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  )
}