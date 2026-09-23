import { useEffect, useState } from 'react'
import type { BusDetails, BusSummary, RouteDto } from '../types'
import { api, friendlyMessage } from '../api'
import AdSlot from './AdSlot'

const statusEmoji = (s: string) =>
  s.toLowerCase() === 'live' ? '🟢' : s.toLowerCase() === 'stale' ? '🟡' : '⚫'

const fmtTime = (iso: string | null | undefined) => {
  if (!iso) return '—'
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

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

const cardinalOf = (deg: number) => {
  const dirs = ['N', 'NNE', 'NE', 'ENE', 'E', 'ESE', 'SE', 'SSE', 'S', 'SSW', 'SW', 'WSW', 'W', 'WNW', 'NW', 'NNW']
  return dirs[Math.round((((deg % 360) + 360) % 360) / 22.5) % 16]
}

export default function BusSheet({
  bus,
  live,
  following,
  onClose,
  onFollow,
  onCenterBus,
  onViewRoute,
  onRoute
}: {
  bus: BusSummary | null
  live: BusSummary | null
  following: boolean
  onClose: () => void
  onFollow: () => void
  onCenterBus?: () => void
  onViewRoute?: () => void
  onRoute?: (r: RouteDto | null) => void
}) {
  const [details, setDetails] = useState<BusDetails | null>(null)
  const [state, setState] = useState<'loading' | 'ok' | 'error'>('loading')
  const [loadKey, setLoadKey] = useState(0)
  const [fav, setFav] = useState(false)
  const [busy, setBusy] = useState(false)
  const [favError, setFavError] = useState<string | null>(null)
  const [shared, setShared] = useState(false)
  const [, setTick] = useState(0)

  useEffect(() => {
    setDetails(null)
    setFav(false)
    setFavError(null)
    setShared(false)
    onRoute?.(null)
    if (!bus) return
    let cancelled = false
    setState('loading')
    api
      .bus(bus.busId)
      .then((d) => {
        if (cancelled) return
        setDetails(d)
        onRoute?.(d.route ?? null)
        setState('ok')
        const routeId = d.route?.routeId
        if (routeId != null) {
          api
            .favorites()
            .then((fs) => setFav(fs.some((f) => f.itemType === 'ROUTE' && f.itemId === routeId)))
            .catch(() => {})
        }
      })
      .catch((e) => {
        if (!cancelled) {
          setState('error')
          setFavError(friendlyMessage(e))
        }
      })
    return () => {
      cancelled = true
    }
  }, [bus, loadKey, onRoute])

  // Keep "last updated" text fresh without re-fetching.
  useEffect(() => {
    const t = window.setInterval(() => setTick((x) => x + 1), 15_000)
    return () => window.clearInterval(t)
  }, [])

  const share = async () => {
    const url = `${window.location.origin}/?bus=${bus?.busId ?? ''}`
    const data = { title: `KA Bus Tracking · ${bus?.registrationNo}`, text: `🚌 Track bus ${bus?.registrationNo} live`, url }
    try {
      if (typeof navigator !== 'undefined' && typeof navigator.share === 'function') {
        await navigator.share(data)
        return
      }
      if (typeof navigator !== 'undefined' && typeof navigator.clipboard?.writeText === 'function') {
        await navigator.clipboard.writeText(url)
        setShared(true)
        window.setTimeout(() => setShared(false), 3000)
        return
      }
      setFavError('Share is not supported in this browser.')
    } catch {
      /* user cancelled the native share sheet */
    }
  }

  if (!bus) return null

  const routeId = details?.route?.routeId ?? null
  const route = details?.route ?? null
  const favouriteTarget = routeId != null ? { itemType: 'ROUTE' as const, itemId: routeId } : null
  const nextStop = details?.nextStop
  const nextStopDistanceKm = details?.nextStopDistanceKm
  const canCenter = bus.latitude != null && bus.longitude != null

  // Prefer the live WebSocket position for telemetry; fall back to the snapshot.
  const liveStatus = (live?.status ?? details?.liveStatus ?? bus.status) as 'LIVE' | 'STALE' | 'OFFLINE'
  const speedKmh = live?.speedKmh ?? details?.speedKmh
  const heading = live?.heading ?? details?.heading
  const accuracyM = live?.accuracyM ?? details?.accuracyM
  const lastUpdate = live?.lastUpdate ?? details?.lastUpdate ?? bus.lastUpdate

  // Following requires a genuinely LIVE fix: a STALE or OFFLINE bus must not be
  // "followed" as if it were moving now.
  const isLive = liveStatus === 'LIVE'
  const canFollow = isLive && live?.latitude != null
  const statusNote =
    liveStatus === 'STALE'
      ? `🟡 Live feed is stale (last fix ${timeAgo(lastUpdate)}). Follow is disabled until a fresh LIVE fix arrives.`
      : liveStatus === 'OFFLINE'
        ? `⚫ This bus is offline (last fix ${timeAgo(lastUpdate)}). The position is no longer live.`
        : null

  const toggleFavorite = async () => {
    if (busy || !favouriteTarget || !details?.route) return
    setBusy(true)
    setFavError(null)
    try {
      if (fav) {
        await api.removeFavorite(favouriteTarget.itemType, favouriteTarget.itemId)
        setFav(false)
      } else {
        await api.addFavorite({
          itemType: 'ROUTE',
          itemId: favouriteTarget.itemId,
          label: details.route.name,
          latitude: bus.latitude,
          longitude: bus.longitude
        })
        setFav(true)
      }
    } catch (e) {
      setFavError(friendlyMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <aside className="sheet" role="dialog" aria-label={`Bus ${bus.registrationNo}`}>
      <button className="sheet-close" onClick={onClose} aria-label="Close bus details">✕</button>

      <div className="sheet-head">
        <h2>🚌 {bus.registrationNo}</h2>
        <span className={`status-chip status-${liveStatus.toLowerCase()}`}>{statusEmoji(liveStatus)} {liveStatus}</span>
      </div>
      {bus.busType && bus.busType !== '—' && <p className="bus-type">ℹ️ {bus.busType}</p>}

      {state === 'loading' && (
        <div className="sheet-loading" aria-label="Loading bus details">
          <div className="skeleton w-70" />
          <div className="skeleton w-50" />
          <div className="skeleton w-80" />
        </div>
      )}

      {state === 'error' && (
        <div className="error-banner" role="alert">
          <p>❌ {favError || 'Could not load live details for this bus.'}</p>
          <button className="retry-btn" onClick={() => setLoadKey((k) => k + 1)}>🔁 Retry</button>
        </div>
      )}

      {state === 'ok' && route && (
        <>
          <p className="route-line">🛣️ {route.origin} ➡️ {route.destination}</p>
          {nextStop && (
            <p className="next-stop">
              📍 Next stop: <strong>{nextStop.name}</strong>{' '}
              {nextStopDistanceKm != null && <span className="dist">📏 {nextStopDistanceKm.toFixed(1)} km away</span>}
            </p>
          )}
          <div className="live-info">
            <div className="info-row">
              <span className="label">Status</span>
              <span className={`status-chip status-${liveStatus.toLowerCase()}`}>
                {statusEmoji(liveStatus)} {liveStatus}
              </span>
            </div>
            <div className="info-row">
              <span className="label">💨 Speed</span>
              <span>{speedKmh != null ? `${speedKmh} km/h` : '—'}</span>
            </div>
            <div className="info-row">
              <span className="label">🧭 Heading</span>
              <span>{heading != null ? `${Math.round(heading)}° ${cardinalOf(heading)}` : '—'}</span>
            </div>
            <div className="info-row">
              <span className="label">🎯 GPS Accuracy</span>
              <span>{accuracyM != null ? `± ${Math.round(accuracyM)} m` : '—'}</span>
            </div>
            <div className="info-row">
              <span className="label">⏱️ Updated</span>
              <span>{lastUpdate ? `${timeAgo(lastUpdate)} · ${fmtTime(lastUpdate)}` : '—'}</span>
            </div>
          </div>
          <p className="feed-note">📡 Position from the live GPS feed. Only real bus fixes are shown.</p>
          {statusNote && <div className="warning-banner" role="status">{statusNote}</div>}

          <div className="sheet-actions">
            {canCenter && (
              <button className="fav-btn" onClick={onCenterBus} aria-label={`Center the map on ${bus.registrationNo}`}>
                📍 Center
              </button>
            )}
            {onViewRoute && (
              <button className="fav-btn" onClick={onViewRoute} aria-label="Show the route on the map">
                🛣️ View Route
              </button>
            )}
            {canFollow && (
              <button className={`fav-btn ${following ? 'active' : ''}`} onClick={onFollow} aria-pressed={following}>
                {following ? '🎯 Following' : '🎯 Follow bus'}
              </button>
            )}
            {favouriteTarget && (
              <button className="fav-btn" onClick={toggleFavorite} disabled={busy}>
                {fav ? '⭐ Saved' : '⭐ Save Route'}
              </button>
            )}
            <button className="fav-btn" onClick={() => void share()} aria-label="Share this bus">
              {shared ? '✅ Link copied' : '🔗 Share'}
            </button>
          </div>
          {favError && state === 'ok' && <p className="form-error" role="alert">⚠️ {favError}</p>}
          {shared && <p className="notice" role="status">🔗 Link copied to clipboard.</p>}

          <h3>🚏 Stops</h3>
          <ol className="stops">
            {route.stops.map((s, i) => {
              const first = i === 0
              const last = i === route.stops.length - 1
              return (
                <li key={s.name}>
                  {first && <span className="trip-tag start">▶️ Start trip</span>}
                  {last && <span className="trip-tag end">⏹️ End trip</span>}
                  {s.name}
                </li>
              )
            })}
          </ol>
        </>
      )}

      {state === 'ok' && !route && (
        <>
          <p className="feed-note">ℹ️ This bus has no live trip right now.</p>
          {statusNote && <div className="warning-banner" role="status">{statusNote}</div>}
          <div className="sheet-actions">
            {canCenter && (
              <button className="fav-btn" onClick={onCenterBus} aria-label={`Center the map on ${bus.registrationNo}`}>
                📍 Center
              </button>
            )}
            {canFollow && (
              <button className={`fav-btn ${following ? 'active' : ''}`} onClick={onFollow} aria-pressed={following}>
                {following ? '🎯 Following' : '🎯 Follow bus'}
              </button>
            )}
            <button className="fav-btn" onClick={() => void share()} aria-label="Share this bus">
              {shared ? '✅ Link copied' : '🔗 Share'}
            </button>
          </div>
        </>
      )}

      <AdSlot placement="PASSENGER_WEB_BUS_DETAILS" />
    </aside>
  )
}