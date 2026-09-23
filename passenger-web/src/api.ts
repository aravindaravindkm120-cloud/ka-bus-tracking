import type {
  BusDetails,
  BusSummary,
  PublicConfig,
  RouteDto,
  RouteGeometryDto,
  SearchResult,
  ServeAdResponse,
  FavoriteItem,
  RecentSearchItem,
  StopSuggestion
} from './types'
import { deviceId } from './device'

const API_BASE = (import.meta.env.VITE_API_URL as string | undefined)
  || ''

export type ApiErrorKind = 'network' | 'timeout' | 'status' | 'unknown'

export class ApiError extends Error {
  readonly kind: ApiErrorKind
  readonly status?: number

  constructor(kind: ApiErrorKind, message: string, status?: number) {
    super(message)
    this.name = 'ApiError'
    this.kind = kind
    this.status = status
  }
}

const REQUEST_TIMEOUT_MS = 15000

async function request(path: string, init: RequestInit): Promise<Response> {
  const ctl = new AbortController()
  const timer = window.setTimeout(() => ctl.abort(), REQUEST_TIMEOUT_MS)
  try {
    let res: Response
    try {
      res = await fetch(`${API_BASE}${path}`, { ...init, signal: ctl.signal })
    } catch (e) {
      if (e instanceof DOMException && e.name === 'AbortError') {
        throw new ApiError('timeout', `Request timed out: ${init.method || 'GET'} ${path}`)
      }
      throw new ApiError('network', `Network unavailable: ${init.method || 'GET'} ${path}`)
    }
    if (!res.ok) {
      throw new ApiError('status', `${init.method || 'GET'} ${path} -> ${res.status}`, res.status)
    }
    return res
  } finally {
    window.clearTimeout(timer)
  }
}

async function get<T>(path: string): Promise<T> {
  const res = await request(path, { headers: { accept: 'application/json' } })
  return res.json() as Promise<T>
}

async function send<T>(path: string, init: RequestInit): Promise<T> {
  const res = await request(path, {
    ...init,
    headers: { 'content-type': 'application/json', 'X-Device-Id': deviceId(), ...(init.headers || {}) }
  })
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}

/** Human-friendly, non-technical message for any caught API error. */
export function friendlyMessage(e: unknown): string {
  if (e instanceof ApiError) {
    switch (e.kind) {
      case 'network':
        return '📡 No internet connection. Please check your connection and try again.'
      case 'timeout':
        return '⏱️ The request timed out. Please try again.'
      case 'status':
        return e.status === 401 || e.status === 403
          ? '🔐 You are not allowed to do that.'
          : '❌ The server could not complete your request. Please try again.'
      default:
        return '❌ Something went wrong. Please try again.'
    }
  }
  return '❌ Something went wrong. Please try again.'
}

export const api = {
  config: () => get<PublicConfig>('/api/public/config'),
  stopSuggestions: (term: string) =>
    get<StopSuggestion[]>(`/api/public/stops?term=${encodeURIComponent(term)}&limit=8`),
  routeSuggestions: (term: string) =>
    get<RouteDto[]>(`/api/public/routes?term=${encodeURIComponent(term)}&limit=8`),
  route: (routeId: number, direction = 'OUTBOUND') =>
    get<RouteDto>(`/api/public/routes/${routeId}?direction=${encodeURIComponent(direction)}`),
  routeGeometry: (routeId: number, direction = 'OUTBOUND') =>
    get<RouteGeometryDto>(`/api/public/routes/${routeId}/geometry?direction=${encodeURIComponent(direction)}`),
  search: (from: string, to: string) =>
    get<SearchResult>(`/api/public/search?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`),
  live: async (limit = 200): Promise<BusSummary[]> => {
    try {
      return await get<BusSummary[]>(`/api/public/live?limit=${limit}`)
    } catch (first) {
      const page = await get<{ content: BusSummary[] }>('/api/public/buses?size=50&page=0')
      return page.content
    }
  },
  bus: (id: number) => get<BusDetails>(`/api/public/buses/${id}`),
  serveAd: (placement: string) => get<ServeAdResponse>(`/api/public/ad/serve?placement=${placement}`),
  recordImpression: (body: { adId: string; placement: string; durationViewedMs: number; clicked: boolean }) =>
    send<void>('/api/public/ad/impression', { method: 'POST', body: JSON.stringify(body) }),
  favorites: () => get<FavoriteItem[]>('/api/public/favorites'),
  addFavorite: (item: { itemType: string; itemId: number; label: string; latitude?: number | null; longitude?: number | null }) =>
    send<FavoriteItem>('/api/public/favorites', {
      method: 'POST',
      body: JSON.stringify({ itemType: item.itemType, itemId: item.itemId, label: item.label, latitude: item.latitude, longitude: item.longitude })
    }),
  removeFavorite: (itemType: string, itemId: number) =>
    send<void>(`/api/public/favorites/${itemType}/${itemId}`, { method: 'DELETE' }),
  recentSearches: () => get<RecentSearchItem[]>('/api/public/searches'),
  recordSearch: (from: string, to: string) =>
    send<void>('/api/public/searches', { method: 'POST', body: JSON.stringify({ from, to }) })
}