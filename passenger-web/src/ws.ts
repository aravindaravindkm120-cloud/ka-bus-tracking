import { Client, type StompSubscription } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import type { WsBusMessage } from './types'

const WS_BASE = (import.meta.env.VITE_WS_URL as string | undefined) || window.location.origin

export type ConnectionStatus = 'connecting' | 'connected' | 'reconnecting'

const BASE_DELAY_MS = 1000
const MAX_DELAY_MS = 30000
/** If no live frame arrives for this long the socket is force-reconnected.
 *  Status itself always comes from the backend; this only guards the
 *  connection indicator from staying glued to a silently dead socket. */
const FRAME_TIMEOUT_MS = 45000

function nextDelay(current: number): number {
  return Math.min(MAX_DELAY_MS, current * 2)
}

/**
 * Thin STOMP-over-SockJS live bus client. Reconnects automatically with a safe
 * exponential backoff (1s → 2s → 4s … capped at 30s). Every reconnect attempt
 * creates a fresh Client so stompjs' own reconnector can never double-schedule;
 * a single timer owns all reactivation. One subscription is kept per
 * connection. The backend broadcasts only public bus data on /topic/live.
 */
export class LiveSocket {
  private client: Client | null = null
  private handler: ((m: WsBusMessage) => void) | null = null
  private onStatus: ((state: ConnectionStatus) => void) | null = null
  private delay = BASE_DELAY_MS
  private activeSub: StompSubscription | null = null
  private disconnected = false
  private frameTimer = 0
  private reconnectTimer = 0

  constructor(onEvent: (m: WsBusMessage) => void, onStatus?: (state: ConnectionStatus) => void) {
    this.handler = onEvent
    this.onStatus = onStatus ?? null
  }

  private clearTimers() {
    window.clearTimeout(this.frameTimer)
    window.clearTimeout(this.reconnectTimer)
    this.frameTimer = 0
    this.reconnectTimer = 0
  }

  connect() {
    if (this.disconnected || this.client) return
    this.onStatus?.('connecting')
    const client = new Client({
      webSocketFactory: () => new SockJS(`${WS_BASE}/ws`) as unknown as WebSocket,
      reconnectDelay: 0,
      heartbeatIncoming: 15000,
      heartbeatOutgoing: 15000,
      onConnect: () => {
        if (this.disconnected || client !== this.client) return
        this.delay = BASE_DELAY_MS
        window.clearTimeout(this.reconnectTimer)
        this.reconnectTimer = 0
        this.onStatus?.('connected')
        this.startFrameWatchdog()
        if (this.activeSub) {
          try { this.activeSub.unsubscribe() } catch { /* already dead */ }
          this.activeSub = null
        }
        this.activeSub = client.subscribe('/topic/live', (frame) => {
          this.startFrameWatchdog()
          try {
            const msg = JSON.parse(frame.body) as WsBusMessage
            this.handler?.(msg)
          } catch {
            /* ignore malformed frames */
          }
        })
      },
      // The old client is thrown away before these fire (see scheduleReconnect),
      // so nothing here races with our own reconnect timer.
      onWebSocketClose: () => { if (this.client) this.scheduleReconnect() },
      onWebSocketError: () => { if (this.client) this.scheduleReconnect() },
      onStompError: () => { if (this.client) this.scheduleReconnect() }
    })
    this.client = client
    client.activate()
  }

  private scheduleReconnect() {
    if (this.disconnected) return
    this.activeSub = null
    this.onStatus?.('reconnecting')
    const wait = this.delay
    this.delay = nextDelay(this.delay)
    const old = this.client
    this.client = null
    if (old) {
      try { old.deactivate() } catch { /* already down */ }
    }
    window.clearTimeout(this.reconnectTimer)
    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = 0
      if (this.disconnected) return
      this.connect()
    }, wait)
  }

  private startFrameWatchdog() {
    window.clearTimeout(this.frameTimer)
    this.frameTimer = window.setTimeout(() => {
      this.frameTimer = 0
      if (this.disconnected || !this.client) return
      // Socket went silent for a long time → force a fresh connection sooner.
      this.delay = BASE_DELAY_MS
      this.scheduleReconnect()
    }, FRAME_TIMEOUT_MS)
  }

  disconnect() {
    this.disconnected = true
    this.clearTimers()
    this.activeSub = null
    const old = this.client
    this.client = null
    if (old) {
      try { old.deactivate() } catch { /* already down */ }
    }
  }
}