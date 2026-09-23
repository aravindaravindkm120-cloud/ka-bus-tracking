export type LiveStatus = 'LIVE' | 'STALE' | 'OFFLINE'

export interface PublicConfig {
  liveSeconds: number
  staleSeconds: number
  liveThresholdSeconds: number
  staleThresholdSeconds: number
  mapBusCap: number
  adsEnabled: boolean
}

export interface StopDto {
  order: number
  name: string
  latitude: number
  longitude: number
  distanceFromStart: number | null
}

export interface RouteDto {
  routeId: number
  code: string
  name: string
  origin: string
  destination: string
  direction: string
  stops: StopDto[]
}

export interface StopSuggestion {
  name: string
  latitude: number | null
  longitude: number | null
}

/** Road-network route geometry from the backend (OSRM), GeoJSON LineString. */
export interface RouteGeometryDto {
  routeId: number | null
  code: string | null
  stopsKey: string
  source: 'cache' | 'osrm'
  available: boolean
  reason: 'ok' | 'routing-unavailable' | 'not-enough-stops' | 'missing-stop-coordinates' | string
  geometry: { type: 'LineString'; coordinates: number[][] } | null
  distanceKm: number | null
  durationSec: number | null
  computedAt: string
}

export interface BusSummary {
  busId: number
  registrationNo: string
  busType: string
  routeId: number | null
  routeName: string | null
  routeCode: string | null
  origin: string | null
  destination: string | null
  latitude: number | null
  longitude: number | null
  speedKmh: number | null
  heading: number | null
  accuracyM: number | null
  status: LiveStatus
  lastUpdate: string | null
}

export interface SearchResult {
  from: string
  to: string
  total: number
  buses: BusSummary[]
  routes: RouteDto[]
}

export interface BusDetails {
  busId: number
  registrationNo: string
  busType: string
  status: string
  tripId: number | null
  tripNumber: string | null
  route: RouteDto | null
  nextStop: StopDto | null
  nextStopDistanceKm: number | null
  latitude: number | null
  longitude: number | null
  speedKmh: number | null
  heading: number | null
  accuracyM: number | null
  altitudeM: number | null
  liveStatus: LiveStatus
  lastUpdate: string | null
}

export interface ServeAdResponse {
  adId: string | null
  campaignId: number | null
  title: string | null
  imageUrl: string | null
  targetUrl: string | null
  durationSeconds: number
  frequencySeconds: number
}

export interface RecentSearchItem {
  from: string
  to: string
  searchedAt: string
}

export interface FavoriteItem {
  favoriteId: number
  itemType: string
  itemId: number
  label: string
  latitude: number | null
  longitude: number | null
}

export interface WsBusMessage {
  busId: number
  tripId: number | null
  routeId: number | null
  latitude: number | null
  longitude: number | null
  speedKmh: number | null
  heading: number | null
  accuracyM: number | null
  altitudeM: number | null
  capturedAt: string
  status: LiveStatus
}