import { useEffect, useRef, useState } from 'react'
import { MapContainer, TileLayer, CircleMarker, Tooltip, Popup, useMap } from 'react-leaflet'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import type { BusSummary, LiveStatus, RouteDto, RouteGeometryDto } from '../types'

const statusEmoji = (s: LiveStatus) => (s === 'LIVE' ? '🟢' : s === 'STALE' ? '🟡' : '⚫')

const esc = (s: string) =>
  s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c] as string))

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

const iconFor = (status: LiveStatus) =>
  L.divIcon({
    className: '',
    html: `<span class="bus-marker">
      <span class="bus-emoji">🚌</span>
      <span class="bus-status-dot bus-${status.toLowerCase()}"></span>
    </span>`,
    iconSize: [30, 34],
    iconAnchor: [15, 30]
  })

function popupHtml(bus: BusSummary): string {
  const meta = [
    bus.speedKmh != null ? `<b>💨 ${Math.round(bus.speedKmh)}</b> km/h` : null,
    bus.heading != null ? `<b>🧭 ${Math.round(bus.heading)}°</b>` : null,
    bus.accuracyM != null ? `<b>🎯 ±${Math.round(bus.accuracyM)} m</b>` : null
  ]
    .filter(Boolean)
    .join(' · ')
  const updated = bus.lastUpdate ? `⏱ Updated ${timeAgo(bus.lastUpdate)}` : '⏱ No update time'
  return `<div class="map-popup">
    <strong class="map-popup-title">🚌 ${esc(bus.registrationNo)}</strong>
    <span class="status-chip status-${bus.status.toLowerCase()}">${statusEmoji(bus.status)} ${bus.status}</span>
    <div class="map-popup-route">🛣️ ${esc(bus.routeName ?? 'No live trip')}</div>
    <div class="map-popup-meta">${meta || '<span class="muted">No live telemetry yet</span>'}</div>
    <div class="map-popup-time">${updated}</div>
  </div>`
}

/** Smoothly ease a Leaflet marker between the previous and new lat/lng. */
function animateMarker(
  marker: L.Marker,
  from: [number, number],
  to: [number, number],
  durationMs: number,
  onDone: () => void
): number {
  const start = performance.now()
  const step = (now: number): number => {
    const t = Math.min(1, (now - start) / durationMs)
    const eased = 1 - Math.pow(1 - t, 3)
    marker.setLatLng([from[0] + (to[0] - from[0]) * eased, from[1] + (to[1] - from[1]) * eased])
    if (t < 1) return requestAnimationFrame(step)
    marker.setLatLng(to)
    onDone()
    return 0
  }
  return requestAnimationFrame(step)
}

/**
 * Keeps Leaflet markers in sync with the live bus list. Markers are animated
 * towards each new GPS fix so the bus moves smoothly instead of jumping, and
 * they are never recreated on every WebSocket frame.
 */
function LiveBusMarkers({
  buses,
  onSelect,
  highlightId
}: {
  buses: BusSummary[]
  onSelect: (b: BusSummary) => void
  highlightId?: number | null
}) {
  const map = useMap()
  const markersRef = useRef<Map<number, L.Marker>>(new Map())
  const animsRef = useRef<Map<number, number>>(new Map())

  useEffect(() => {
    const markers = markersRef.current
    const alive = new Set<number>()

    for (const bus of buses) {
      if (bus.latitude == null || bus.longitude == null) continue
      alive.add(bus.busId)

      let m = markers.get(bus.busId)
      if (!m) {
        m = L.marker([bus.latitude, bus.longitude], { icon: iconFor(bus.status), keyboard: true })
        m.on('click', () => onSelect(bus))
        m.bindTooltip(bus.registrationNo, { direction: 'top', offset: [0, -24], opacity: 0.92 })
        m.addTo(map)
        markers.set(bus.busId, m)
      }

      m.setIcon(iconFor(bus.status))
      m.bindPopup(popupHtml(bus))
      m.setPopupContent(popupHtml(bus))
      m.setTooltipContent(esc(bus.registrationNo))

      const target: [number, number] = [bus.latitude, bus.longitude]
      const cur = m.getLatLng()
      const moved = Math.abs(cur.lat - target[0]) > 1e-6 || Math.abs(cur.lng - target[1]) > 1e-6
      if (moved) {
        const prev = animsRef.current.get(bus.busId)
        if (prev != null) cancelAnimationFrame(prev)
        const wrapDone = () => animsRef.current.delete(bus.busId)
        animsRef.current.set(bus.busId, animateMarker(m, [cur.lat, cur.lng], target, 700, wrapDone))
      }
    }

    for (const [id, m] of markers) {
      if (!alive.has(id)) {
        const raf = animsRef.current.get(id)
        if (raf != null) cancelAnimationFrame(raf)
        animsRef.current.delete(id)
        m.remove()
        markers.delete(id)
      }
    }
  }, [buses, map, onSelect])

  useEffect(() => {
    for (const [id, m] of markersRef.current) {
      if (id === highlightId) m.openTooltip()
      else m.closeTooltip()
    }
  }, [highlightId])

  useEffect(
    () => () => {
      for (const raf of animsRef.current.values()) cancelAnimationFrame(raf)
      animsRef.current.clear()
      markersRef.current.forEach((m) => m.remove())
      markersRef.current.clear()
    },
    []
  )

  return null
}

/** The passenger's own real position from the browser geolocation API. */
function MyLocationMarker({ onError }: { onError: (msg: string) => void }) {
  const [pos, setPos] = useState<[number, number] | null>(null)
  const reported = useRef(false)

  useEffect(() => {
    if (typeof navigator === 'undefined' || !navigator.geolocation) return
    const watch = navigator.geolocation.watchPosition(
      (p) => setPos([p.coords.latitude, p.coords.longitude]),
      (err) => {
        if (reported.current) return
        reported.current = true
        if (err.code === err.PERMISSION_DENIED)
          onError('📍 Location permission denied. Enable location access to use the My Location button.')
        else onError('📍 Could not get your location. Try again.')
      },
      { enableHighAccuracy: true, maximumAge: 10000, timeout: 20000 }
    )
    return () => {
      if (watch != null) navigator.geolocation.clearWatch(watch)
    }
  }, [onError])

  if (!pos) return null
  return (
    <>
      <CircleMarker
        center={pos}
        radius={9}
        pathOptions={{ color: '#1565c0', weight: 2, fillColor: '#42a5f5', fillOpacity: 0.45 }}
      >
        <Tooltip direction="top" offset={[0, -4]} permanent className="device-label">
          📍 My Location
        </Tooltip>
        <Popup>
          <strong>📍 My Location</strong>
          <div>You are here</div>
        </Popup>
      </CircleMarker>
      <CircleMarker center={pos} radius={24} pathOptions={{ color: '#42a5f5', weight: 1, fillOpacity: 0 }} />
    </>
  )
}

/** Fits the camera to a target set exactly once per fitToken. */
function FocusFitter({ token, buses }: { token: string; buses: BusSummary[] }) {
  const map = useMap()
  const lastToken = useRef<string | null>(null)
  const fitted = useRef(false)
  useEffect(() => {
    if (!token || !buses.length) {
      // No active target; never yank the camera to a different view.
      fitted.current = false
      return
    }
    if (token !== lastToken.current) {
      lastToken.current = token
      fitted.current = false
    }
    if (fitted.current) return
    const live = buses.filter((b) => b.latitude != null && b.longitude != null)
    if (live.length === 0) return
    fitted.current = true
    map.fitBounds(
      live.map((b) => [b.latitude as number, b.longitude as number] as [number, number]),
      { padding: [46, 46], maxZoom: 15 }
    )
  }, [token, buses, map])
  return null
}

/** Keeps the map centred on a followed bus until the user drags the map. */
function FollowPulse({
  followId,
  buses,
  onInterrupt
}: {
  followId: number | null
  buses: BusSummary[]
  onInterrupt?: () => void
}) {
  const map = useMap()
  const lastPos = useRef<[number, number] | null>(null)

  useEffect(() => {
    if (!followId) {
      lastPos.current = null
      return
    }
    const b = buses.find((x) => x.busId === followId)
    if (!b || b.latitude == null || b.longitude == null) return
    const pos: [number, number] = [b.latitude, b.longitude]
    if (
      !lastPos.current ||
      Math.abs(lastPos.current[0] - pos[0]) > 1e-5 ||
      Math.abs(lastPos.current[1] - pos[1]) > 1e-5
    ) {
      map.panTo(pos, { animate: true, duration: 0.6 })
      lastPos.current = pos
    }
  }, [buses, followId, map])

  useEffect(() => {
    if (!followId) return
    const onDrag = () => onInterrupt?.()
    map.on('dragstart', onDrag)
    return () => {
      map.off('dragstart', onDrag)
    }
  }, [map, followId, onInterrupt])

  return null
}

/**
 * Draws the map routes: real OSRM road-network geometry as polylines plus stop
 * markers at their real coordinates. When no road geometry is available a route
 * is never invented — only stop locations are shown and the caller shows an
 * explicit state, so a fake straight line can never appear.
 */
function RouteLayer({ routes, geometries }: { routes: RouteDto[]; geometries: Record<number, RouteGeometryDto> }) {
  const map = useMap()
  useEffect(() => {
    const lyr = L.layerGroup()
    for (const route of routes) {
      const geometry = geometries[route.routeId] ?? null
      const geo = geometry?.available ? geometry.geometry : null
      if (geo && geo.coordinates.length) {
        const latlngs = geo.coordinates.map(([lon, lat]) => [lat, lon] as [number, number])
        L.polyline(latlngs, { color: '#0b6e51', weight: 4, opacity: 0.9 }).addTo(lyr)
      }
      route.stops
        .filter((s) => s.latitude != null && s.longitude != null)
        .forEach((s) => {
          L.circleMarker([s.latitude as number, s.longitude as number], {
            radius: 5,
            color: '#fff',
            weight: 2,
            fillColor: '#0b6e51',
            fillOpacity: 1
          })
            .addTo(lyr)
            .bindTooltip(s.name, { direction: 'top', offset: [0, -6] })
        })
    }
    if (lyr.getLayers().length) lyr.addTo(map)
    return () => {
      lyr.remove()
    }
  }, [routes, geometries, map])
  return null
}

const ROUTE_FIT_PADDING: L.FitBoundsOptions = { padding: [70, 70], maxZoom: 16 }

/**
 * Fits the map to the current route. Runs automatically once per route when its
 * geometry has settled (success or failure) and again when "View Route" is
 * pressed. Geometry-based bounds are preferred, with the real stop coordinates
 * as a fallback, so the view always covers the actual route corridor (never a
 * hardcoded city).
 */
function RouteFitter({
  routes,
  geometries,
  geometryLoading,
  fitKey
}: {
  routes: RouteDto[]
  geometries: Record<number, RouteGeometryDto>
  geometryLoading: boolean
  fitKey: number
}) {
  const map = useMap()
  const last = useRef<{ key: number; token: string } | null>(null)

  useEffect(() => {
    if (routes.length === 0) {
      last.current = null
      return
    }
    // Wait for the geometry round-trip so the fit uses the real road bounds.
    if (geometryLoading) return
    const token = routes
      .map((r) => `${r.routeId}:${geometries[r.routeId]?.available ? 'geometry' : 'stops'}`)
      .join('|')
    if (last.current && last.current.key === fitKey && last.current.token === token) return
    last.current = { key: fitKey, token }

    const pts: [number, number][] = []
    for (const route of routes) {
      const geometry = geometries[route.routeId] ?? null
      const geo = geometry?.available ? geometry.geometry : null
      if (geo && geo.coordinates.length) {
        for (const [lon, lat] of geo.coordinates) pts.push([lat, lon])
      } else {
        route.stops
          .filter((s) => s.latitude != null && s.longitude != null)
          .forEach((s) => pts.push([s.latitude as number, s.longitude as number]))
      }
    }
    if (pts.length < 2) return
    map.fitBounds(L.latLngBounds(pts), ROUTE_FIT_PADDING)
  }, [routes, geometries, geometryLoading, fitKey, map])
  return null
}

/** One-shot "Center Bus": flies the camera to the selected bus when asked. */
function CenterBus({
  buses,
  busId,
  token
}: {
  buses: BusSummary[]
  busId: number | null
  token: number
}) {
  const map = useMap()
  useEffect(() => {
    if (token === 0 || busId == null) return
    const b = buses.find((x) => x.busId === busId)
    if (!b || b.latitude == null || b.longitude == null) return
    map.flyTo([b.latitude, b.longitude], Math.max(map.getZoom(), 15), { duration: 0.8 })
  }, [token, busId, buses, map])
  return null
}

/**
 * Keeps the Leaflet map sized correctly: listens to container resizes,
 * browser window resizes and orientation changes. Prevents the common
 * "map frozen / grey after the layout changes" bug.
 */
function MapResize() {
  const map = useMap()
  useEffect(() => {
    const el = map.getContainer()
    if (!el) return
    // Classify after the first paint in case the container was 0-sized.
    const t = window.setTimeout(() => map.invalidateSize(), 0)
    let ro: ResizeObserver | null = null
    if (typeof ResizeObserver !== 'undefined') {
      ro = new ResizeObserver(() => map.invalidateSize())
      ro.observe(el)
    }
    const onWinResize = () => map.invalidateSize()
    window.addEventListener('resize', onWinResize)
    window.addEventListener('orientationchange', onWinResize)
    return () => {
      window.clearTimeout(t)
      ro?.disconnect()
      window.removeEventListener('resize', onWinResize)
      window.removeEventListener('orientationchange', onWinResize)
    }
  }, [map])
  return null
}

export default function MapView({
  buses,
  onSelect,
  selected,
  followId,
  onFollow,
  onInterrupt,
  focusBuses,
  fitToken,
  routes,
  geometries,
  geometryLoading,
  fitKey,
  centerBusId,
  centerBusKey,
  onCenterBus,
  onLocationError
}: {
  buses: BusSummary[]
  onSelect: (b: BusSummary) => void
  selected?: BusSummary | null
  followId?: number | null
  onFollow?: (id: number | null) => void
  onInterrupt?: () => void
  focusBuses?: BusSummary[]
  fitToken?: string
  routes?: RouteDto[]
  geometries?: Record<number, RouteGeometryDto>
  geometryLoading?: boolean
  fitKey?: number
  centerBusId?: number | null
  centerBusKey?: number
  onCenterBus?: () => void
  onLocationError?: (msg: string) => void
}) {
  const mapRef = useRef<L.Map | null>(null)
  const positioned = buses.filter((b) => b.latitude != null && b.longitude != null)
  const following = followId ?? null
  const canFollow = selected != null && selected.latitude != null && selected.longitude != null
  const handleLocationError = onLocationError ?? (() => {})

  const liveCount = buses.filter((b) => b.status === 'LIVE').length
  const staleCount = buses.filter((b) => b.status === 'STALE').length
  const offlineCount = buses.filter((b) => b.status === 'OFFLINE').length

  const locateMe = () => {
    const map = mapRef.current
    if (!map) return
    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      handleLocationError('📍 Location is not supported on this device or browser.')
      return
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        map.flyTo([pos.coords.latitude, pos.coords.longitude], Math.max(map.getZoom(), 15), { duration: 0.8 })
      },
      (err) => {
        if (err.code === err.PERMISSION_DENIED)
          handleLocationError('📍 Location permission denied. Enable location access to use the My Location button.')
        else handleLocationError('📍 Could not get your location. Try again.')
      },
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 30000 }
    )
  }

  return (
    <div className="map-wrap">
      <MapContainer
        center={[13.9756, 75.5334]}
        zoom={6}
        className="map"
        zoomControl={false}
        ref={mapRef}
        doubleClickZoom
        scrollWheelZoom
        touchZoom
        dragging
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
          url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
          maxZoom={19}
        />
        <MapResize />
        <FocusFitter token={fitToken ?? ''} buses={focusBuses ?? buses} />
        <FollowPulse followId={following} buses={buses} onInterrupt={onInterrupt} />
        <MyLocationMarker onError={handleLocationError} />
        <LiveBusMarkers buses={positioned} onSelect={onSelect} highlightId={selected?.busId ?? following} />
        <RouteLayer routes={routes ?? []} geometries={geometries ?? {}} />
        <RouteFitter
          routes={routes ?? []}
          geometries={geometries ?? {}}
          geometryLoading={geometryLoading ?? false}
          fitKey={fitKey ?? 0}
        />
        <CenterBus buses={buses} busId={centerBusId ?? null} token={centerBusKey ?? 0} />
      </MapContainer>

      {routes != null && routes.length > 0 && (geometryLoading ?? false) && (
        <div className="route-unavailable" role="status">
          🛣️ Loading route…
        </div>
      )}
      {routes != null &&
        routes.length > 0 &&
        !(geometryLoading ?? false) &&
        routes.some((r) => !(geometries?.[r.routeId]?.available ?? false)) && (
          <div className="route-unavailable" role="status">
            🛣️ Route map is temporarily unavailable for some routes. Showing real stop locations only.
          </div>
        )}

      <div className="map-controls" role="group" aria-label="Map controls">
        <button
          className="map-btn"
          aria-label="Zoom in"
          title="Zoom in"
          onClick={() => mapRef.current?.zoomIn()}
        >
          ＋
        </button>
        <button
          className="map-btn"
          aria-label="Zoom out"
          title="Zoom out"
          onClick={() => mapRef.current?.zoomOut()}
        >
          −
        </button>
        <button className="map-btn" aria-label="Show my location" title="My location" onClick={locateMe}>
          📍
        </button>
        {canFollow && (
          <button
            className="map-btn"
            aria-label="Center map on the selected bus"
            title="Center bus"
            onClick={onCenterBus}
          >
            🚌
          </button>
        )}
        <button
          className={`map-btn ${following ? 'active' : ''}`}
          aria-label={following ? 'Stop following bus' : 'Follow selected bus'}
          title={following ? 'Stop following' : 'Follow selected bus'}
          disabled={!canFollow}
          onClick={() => {
            if (!canFollow) return
            onFollow?.(following ? null : (selected as BusSummary).busId)
          }}
        >
          🎯
        </button>
      </div>

      <div className="map-legend" aria-label="Bus status legend">
        <span className="legend-item"><span className="legend-dot legend-live" />{liveCount} live</span>
        <span className="legend-item"><span className="legend-dot legend-stale" />{staleCount} stale</span>
        <span className="legend-item"><span className="legend-dot legend-offline" />{offlineCount} offline</span>
      </div>
    </div>
  )
}