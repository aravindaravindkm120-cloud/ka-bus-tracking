import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import MapView from './components/MapView'
import SearchPanel from './components/SearchPanel'
import HeaderScene from './components/HeaderScene'
import ListPanel from './components/ListPanel'
import BusSheet from './components/BusSheet'
import AdSlot from './components/AdSlot'
import HeaderFlags from './components/HeaderFlags'
import { LiveSocket, type ConnectionStatus } from './ws'
import { api, friendlyMessage } from './api'
import type { BusSummary, RouteDto, RouteGeometryDto, SearchResult, WsBusMessage } from './types'

interface SearchState {
  from: string
  to: string
  buses: BusSummary[]
}

interface BannerState {
  kind: 'error' | 'info'
  text: string
}

/**
 * Merge one WebSocket frame into a bus list without losing route metadata.
 * Buses that end their stream (OFFLINE with no coordinate) are removed.
 */
const mergeLive = (list: BusSummary[], m: WsBusMessage): BusSummary[] => {
  if (m.status === 'OFFLINE' && m.latitude == null) {
    return list.filter((b) => b.busId !== m.busId)
  }
  if (m.latitude == null) return list
  const existing = list.find((b) => b.busId === m.busId)
  const updated: BusSummary = {
    busId: m.busId,
    registrationNo: existing?.registrationNo ?? `Bus ${m.busId}`,
    busType: existing?.busType ?? '—',
    routeId: m.routeId,
    routeName: existing?.routeName ?? (m.routeId != null ? `Trip ${m.routeId}` : null),
    routeCode: existing?.routeCode ?? null,
    origin: existing?.origin ?? null,
    destination: existing?.destination ?? null,
    latitude: m.latitude,
    longitude: m.longitude,
    speedKmh: m.speedKmh,
    heading: m.heading,
    accuracyM: m.accuracyM,
    status: m.status,
    lastUpdate: m.capturedAt
  }
  return existing ? list.map((b) => (b.busId === m.busId ? updated : b)) : [...list, updated]
}

interface HistoryState {
  v?: 'home' | 'search' | 'bus'
  from?: string
  to?: string
  bus?: number
}

export default function App() {
  const [buses, setBuses] = useState<BusSummary[] | null>(null)
  const [liveError, setLiveError] = useState<string | null>(null)
  const [search, setSearch] = useState<SearchState | null>(null)
  const [selected, setSelected] = useState<BusSummary | null>(null)
  const [initialFocus, setInitialFocus] = useState<{ busId: number } | null>(null)
  const [selectedRoute, setSelectedRoute] = useState<RouteDto | null>(null)
  const [searchRoutes, setSearchRoutes] = useState<RouteDto[]>([])
  const [searchDirty, setSearchDirty] = useState(false)
  const [routeGeometries, setRouteGeometries] = useState<Record<number, RouteGeometryDto>>({})
  const [routeGeometryLoading, setRouteGeometryLoading] = useState(false)
  const [routeFitKey, setRouteFitKey] = useState(0)
  const [centerBusKey, setCenterBusKey] = useState(0)
  const [conn, setConn] = useState<ConnectionStatus>('connecting')
  const [followId, setFollowId] = useState<number | null>(null)
  const [searchVersion, setSearchVersion] = useState(0)
  const [prefill, setPrefill] = useState({ from: '', to: '' })
  const [notice, setNotice] = useState<string | null>(null)
  const [banner, setBanner] = useState<BannerState | null>(null)

  const searchRef = useRef<SearchState | null>(null)
  searchRef.current = search
  const selectedRef = useRef<BusSummary | null>(null)
  selectedRef.current = selected
  const searchSeqRef = useRef(0)
  const bannerTimer = useRef(0)
  const firstConnect = useRef(true)

  const showBanner = useCallback((kind: BannerState['kind'], text: string) => {
    setBanner({ kind, text })
    window.clearTimeout(bannerTimer.current)
    bannerTimer.current = window.setTimeout(() => setBanner(null), 8000)
  }, [])

  const applyWs = useCallback((m: WsBusMessage) => {
    setBuses((prev) => mergeLive(prev ?? [], m))
    setSearch((prev) => (prev ? { ...prev, buses: mergeLive(prev.buses, m) } : prev))
  }, [])

  const refreshLive = useCallback(
    async (silent = false): Promise<boolean> => {
      try {
        const list = await api.live(200)
        setBuses(list)
        setLiveError(null)
        return true
      } catch (e) {
        if (!silent) showBanner('error', friendlyMessage(e))
        return false
      }
    },
    [showBanner]
  )

  /// ---- navigation ---------------------------------------------------------

  const goHome = useCallback(() => {
    searchSeqRef.current += 1
    setSearch(null)
    setSelected(null)
    setInitialFocus(null)
    setSelectedRoute(null)
    setSearchRoutes([])
    setRouteGeometries({})
    setFollowId(null)
    setSearchDirty(false)
    setNotice(null)
    setBanner(null)
    setPrefill({ from: '', to: '' })
    setSearchVersion((v) => v + 1)
    window.history.replaceState({ v: 'home' }, '', window.location.pathname)
    void refreshLive(false)
  }, [refreshLive])

  const back = useCallback(() => {
    if (selected) {
      const st = window.history.state as HistoryState | null
      if (st?.v === 'bus') {
        window.history.back()
        return
      }
    }
    if (search) {
      goHome()
      return
    }
    if (window.history.length > 1) {
      window.history.back()
    } else {
      goHome()
    }
  }, [selected, search, goHome])

  const selectBus = useCallback((b: BusSummary) => {
    setSelected(b)
    const st = window.history.state as HistoryState | null
    if (st?.v === 'bus' && st.bus === b.busId) return
    const from = searchRef.current?.from
    const to = searchRef.current?.to
    const q = new URLSearchParams()
    if (from && to) {
      q.set('from', from)
      q.set('to', to)
    }
    q.set('bus', String(b.busId))
    window.history.pushState({ v: 'bus', bus: b.busId, from, to }, '', `/?${q.toString()}`)
  }, [])

  const closeSheet = useCallback(() => {
    setSelected(null)
    setSelectedRoute(null)
    setFollowId(null)
    const st = window.history.state as HistoryState | null
    if (st?.v === 'bus') window.history.back()
  }, [])

  /// ---- search lifecycle ---------------------------------------------------

  /**
   * Execute (or restore) a committed search. Only the newest call wins; older
   * responses are dropped so a slow "Karwar -> Honnavar" can never overwrite a
   * newer "Karwar -> Kadwad". The FROM/TO used afterwards are the canonical
   * official names returned by the backend.
   */
  const commitSearch = useCallback(
    async (from: string, to: string, opts?: { busId?: number; record?: boolean; replace?: boolean }) => {
      const seq = ++searchSeqRef.current
      const prevSelected = selectedRef.current
      setSearchDirty(false)
      // Drop stale selection/route/follow state immediately so an old bus
      // sheet or route line can never survive into the new search.
      setSelected(null)
      setInitialFocus(null)
      setFollowId(null)
      setSelectedRoute(null)
      setNotice(null)

      let res: SearchResult
      try {
        res = await api.search(from, to)
      } catch (e) {
        setLiveError(friendlyMessage(e))
        throw e
      }
      if (seq !== searchSeqRef.current) return // a newer search won the race

      if (opts?.record !== false) api.recordSearch(res.from, res.to).catch(() => {})

      setSearch({ from: res.from, to: res.to, buses: res.buses })
      setPrefill({ from: res.from, to: res.to })
      setSearchVersion((v) => v + 1)
      setSearchRoutes(res.routes)

      // Keep a previously selected bus only when it is genuinely part of the
      // new search's results; otherwise it must not linger with old data.
      let decidedBus: BusSummary | null = null
      const stillValid = prevSelected
        ? res.buses.find((b) => b.busId === prevSelected.busId) ?? null
        : null
      if (stillValid) {
        setSelected(stillValid)
        decidedBus = stillValid
      } else if (opts?.busId != null) {
        const b = res.buses.find((x) => x.busId === opts.busId) ?? null
        if (b) {
          setSelected(b)
          decidedBus = b
        } else {
          setInitialFocus({ busId: opts.busId })
        }
      }

      // URL = the committed search only. The bus param is kept only when that
      // bus actually belongs to this search.
      let qs = `from=${encodeURIComponent(res.from)}&to=${encodeURIComponent(res.to)}`
      if (decidedBus) qs += `&bus=${decidedBus.busId}`
      const url = `/?${qs}`
      const cur = window.history.state as HistoryState | null
      const sameRoute =
        cur?.v === 'search' && cur.from === res.from && cur.to === res.to
      const state: HistoryState = { v: 'search', from: res.from, to: res.to }
      if (opts?.replace) {
        window.history.replaceState(state, '', url)
      } else if (!sameRoute) {
        window.history.pushState(state, '', url)
      } else {
        window.history.replaceState(state, '', url)
      }
    },
    []
  )

  const onSearchRequested = useCallback(
    async (from: string, to: string): Promise<void> => {
      await commitSearch(from, to)
    },
    [commitSearch]
  )

  const openRecent = useCallback(
    (from: string, to: string) => {
      commitSearch(from, to).catch((e) => setNotice(friendlyMessage(e)))
    },
    [commitSearch]
  )

  const openFavorite = useCallback(
    (label: string) => {
      const arrow = label.indexOf('→')
      if (arrow > 0) {
        const f = label.slice(0, arrow).trim()
        const t = label.slice(arrow + 1).trim()
        if (f && t) {
          openRecent(f, t)
          return
        }
      }
      goHome()
    },
    [openRecent, goHome]
  )

  /// ---- editing (dirty) state ----------------------------------------------

  const clearActiveResults = useCallback(() => {
    setSelected(null)
    setSelectedRoute(null)
    setFollowId(null)
    setSearchRoutes([])
    setRouteGeometries({})
    setInitialFocus(null)
  }, [])

  const handleDirtyChange = useCallback(
    (dirty: boolean, fromEmpty: boolean, toEmpty: boolean) => {
      setSearchDirty(dirty)
      if (!dirty) return
      // Inputs no longer match the committed search: never present the old
      // route/bus as the active result for the text being edited.
      clearActiveResults()
      if (fromEmpty && toEmpty) {
        // Both inputs cleared: the active search itself must go.
        setSearch(null)
        setNotice(null)
      }
    },
    [clearActiveResults]
  )

  const restoreFromHistory = useCallback(
    async (st: HistoryState) => {
      if (!st.v || st.v === 'home') {
        searchSeqRef.current += 1
        setSearch(null)
        setSelected(null)
        setInitialFocus(null)
        setSelectedRoute(null)
        setSearchRoutes([])
        setRouteGeometries({})
        setFollowId(null)
        setSearchDirty(false)
        setPrefill({ from: '', to: '' })
        setSearchVersion((v) => v + 1)
        return
      }
      if (st.v === 'search' && st.from && st.to) {
        await commitSearch(st.from, st.to, { record: false, replace: true })
        return
      }
      if (st.v === 'bus' && st.from && st.to && st.bus != null) {
        await commitSearch(st.from, st.to, { record: false, replace: true, busId: st.bus })
      }
    },
    [commitSearch]
  )

  /// ---- boot / websocket ---------------------------------------------------

  useEffect(() => {
    let cancelled = false
    const params = new URLSearchParams(window.location.search)
    const f = params.get('from')
    const t = params.get('to')
    const b = params.get('bus')
    const initial = { v: 'search', from: f, to: t } as HistoryState

    const ws = new LiveSocket(
      (m) => applyWs(m),
      (s) => {
        if (cancelled) return
        setConn(s)
        if (s === 'connected' && !firstConnect.current) {
          // Fill any frames missed while the socket was down.
          void refreshLive(true)
        }
        firstConnect.current = false
      }
    )
    ws.connect()

    const poll = window.setInterval(() => {
      if (cancelled || document.hidden || window.navigator.onLine === false) return
      void refreshLive(true)
    }, 30_000)

    void api
      .config()
      .catch(() => null)
      .then(async (cfg) => {
        if (cancelled) return
        let list: BusSummary[] = []
        try {
          list = await api.live(cfg?.mapBusCap ?? 200)
        } catch (e) {
          if (!cancelled) {
            setLiveError(friendlyMessage(e))
            setBuses([])
          }
          return
        }
        if (cancelled) return
        setBuses(list)
        if (f && t) {
          const busId = b != null ? Number(b) : undefined
          await restoreFromHistory({ v: 'search', from: f, to: t, bus: busId })
        } else if (b && !Number.isNaN(Number(b))) {
          const id = Number(b)
          const target = list.find((x) => x.busId === id) ?? null
          if (target) {
            setSelected(target)
            setInitialFocus({ busId: id })
          }
          if (!cancelled) {
            const cur = window.history.state as HistoryState | null
            if (!cur?.v) window.history.replaceState(initial, '', window.location.search)
          }
        }
      })

    const onPop = (e: PopStateEvent) => {
      void restoreFromHistory((e.state as HistoryState) ?? { v: 'home' })
    }
    window.addEventListener('popstate', onPop)

    return () => {
      cancelled = true
      window.clearInterval(poll)
      window.removeEventListener('popstate', onPop)
      ws.disconnect()
    }
  }, [applyWs, refreshLive, restoreFromHistory])

  /// ---- derived data -------------------------------------------------------

  // Routes shown on the map: the committed search corridors plus, when a bus
  // is open, that bus's own route. Nothing is shown while the inputs are being
  // edited (dirty).
  const mapRoutes = useMemo(() => {
    if (searchDirty) return []
    const seen = new Set<string>()
    const out: RouteDto[] = []
    for (const r of [...searchRoutes, ...(selectedRoute ? [selectedRoute] : [])]) {
      const k = `${r.routeId}:${r.direction}`
      if (seen.has(k)) continue
      seen.add(k)
      out.push(r)
    }
    return out
  }, [searchRoutes, selectedRoute, searchDirty])
  const routeKey = mapRoutes.map((r) => `${r.routeId}:${r.direction}`).join('|')

  // Fetch real OSRM road geometry for every displayed route. The backend shares
  // a central cache, so many passengers on the same route trigger one OSRM call.
  useEffect(() => {
    if (!routeKey) {
      setRouteGeometries({})
      setRouteGeometryLoading(false)
      return
    }
    let cancelled = false
    setRouteGeometryLoading(true)
    const routes = mapRoutes
    Promise.all(
      routes.map(async (r) => {
        try {
          const g = await api.routeGeometry(r.routeId, r.direction)
          return { routeId: r.routeId, geometry: g }
        } catch {
          return { routeId: r.routeId, geometry: null }
        }
      })
    )
      .then((entries) => {
        if (cancelled) return
        const obj: Record<number, RouteGeometryDto> = {}
        for (const e of entries) if (e.geometry) obj[e.routeId] = e.geometry
        setRouteGeometries(obj)
        setRouteFitKey((k) => k + 1)
      })
      .finally(() => {
        if (!cancelled) setRouteGeometryLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [routeKey, mapRoutes])

  // Following is only meaningful for a bus that is genuinely live. Stop the
  // automatic follow as soon as the selected bus is no longer LIVE (STALE is a
  // warning, OFFLINE is a hard stop) or the selection moves elsewhere.
  const liveSelectedResolved = selected ? (buses ?? []).find((b) => b.busId === selected.busId) ?? selected : null
  useEffect(() => {
    if (followId == null) return
    if (selected?.busId !== followId) {
      setFollowId(null)
      return
    }
    if (liveSelectedResolved && liveSelectedResolved.status !== 'LIVE') {
      setFollowId(null)
    }
  }, [followId, selected?.busId, liveSelectedResolved?.status, liveSelectedResolved?.busId])

  const displayed = search ? search.buses : (buses ?? [])
  const fitToken =
    search && !searchDirty
      ? `${search.from}→${search.to}`
      : initialFocus
        ? `init-${initialFocus.busId}`
        : ''
  const focusBuses = !search || searchDirty
    ? initialFocus
      ? (buses ?? []).filter((b) => b.busId === initialFocus.busId)
      : []
    : search.buses
  const initialLoading = buses === null && !search
  const showBack = !!(selected || search)

  const busOpen = selected != null

  return (
    <div className={busOpen ? 'app app-bus-open' : 'app'}>
      <header className="topbar">
        <div className="topbar-row">
          <div className="topbar-main">
            {showBack && (
              <button className="back-btn" onClick={back} aria-label="Go back" title="Go back">
                <span aria-hidden="true">←</span>
                <span className="back-label">Back</span>
              </button>
            )}
            <div className="brand">
              <span className="brand-badge" aria-hidden="true">🚌</span>
              <div className="brand-text">
                <h1>KA Bus Tracking</h1>
                <p className={`conn-status conn-${conn}`} role="status" aria-live="polite">
                  {conn === 'connected'
                    ? '📡 Live feed connected'
                    : conn === 'reconnecting'
                      ? '📡 Reconnecting…'
                      : '📡 Connecting…'}
                </p>
              </div>
            </div>
          </div>
          <HeaderFlags />
          <div className="topbar-actions">
            <button className="icon-btn" onClick={() => { goHome(); }} aria-label="Home – live buses" title="Home (live buses)">🏠</button>
            <button className="icon-btn" onClick={() => void refreshLive(false)} aria-label="Refresh live buses" title="Refresh live buses">🔄</button>
          </div>
        </div>
        <SearchPanel
          key={`${searchVersion}:${prefill.from}:${prefill.to}`}
          initialFrom={prefill.from}
          initialTo={prefill.to}
          committedFrom={search?.from ?? ''}
          committedTo={search?.to ?? ''}
          onDirtyChange={handleDirtyChange}
          onSearch={onSearchRequested}
        />
        <HeaderScene />
      </header>

      {banner && (
        <div className={`app-banner ${banner.kind === 'error' ? 'banner-error' : 'banner-info'}`} role={banner.kind === 'error' ? 'alert' : 'status'}>
          <span>{banner.text}</span>
          <button className="banner-close" onClick={() => setBanner(null)} aria-label="Dismiss message">✕</button>
        </div>
      )}

      <main className="map-stage">
        <MapView
          buses={buses ?? []}
          onSelect={selectBus}
          selected={liveSelectedResolved ?? selected}
          followId={followId}
          onFollow={setFollowId}
          onInterrupt={() => setFollowId(null)}
          focusBuses={focusBuses}
          fitToken={fitToken}
          routes={mapRoutes}
          geometries={routeGeometries}
          geometryLoading={routeGeometryLoading}
          fitKey={routeFitKey}
          centerBusId={selected?.busId ?? null}
          centerBusKey={centerBusKey}
          onCenterBus={() => setCenterBusKey((k) => k + 1)}
          onLocationError={(msg) => showBanner('info', msg)}
        />
      </main>

      <ListPanel
        onSelect={selectBus}
        onRefresh={() => refreshLive(false)}
        onRecent={openRecent}
        onFavorite={openFavorite}
        from={search?.from ?? ''}
        to={search?.to ?? ''}
        buses={displayed}
        notice={notice}
        loading={initialLoading}
        liveError={liveError}
      />
      <BusSheet
        bus={selected}
        live={liveSelectedResolved}
        following={followId === selected?.busId}
        onClose={closeSheet}
        onFollow={() => {
          if (selected) setFollowId((cur) => (cur === selected.busId ? null : selected.busId))
        }}
        onCenterBus={() => setCenterBusKey((k) => k + 1)}
        onViewRoute={() => setRouteFitKey((k) => k + 1)}
        onRoute={setSelectedRoute}
      />

      {!selected && !search && <div className="bottom-ad"><AdSlot placement="PASSENGER_WEB_MAP_BOTTOM" /></div>}
    </div>
  )
}