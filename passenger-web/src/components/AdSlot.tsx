import { useEffect, useRef, useState } from 'react'
import type { ServeAdResponse } from '../types'
import { api } from '../api'

/**
 * Ad slot. Only reports an impression when the ad has genuinely been visible
 * on screen for at least 1 second (IntersectionObserver). Ads never overlay
 * the bus controls or block the map - they are a small bottom banner in the
 * app's own layout. frequencySeconds (per placement) throttles re-display.
 */
export default function AdSlot({ placement }: { placement: string }) {
  const [ad, setAd] = useState<ServeAdResponse | null>(null)
  const boxRef = useRef<HTMLDivElement | null>(null)
  const shownRef = useRef<{ ok: boolean; timer: number }>({ ok: false, timer: 0 })
  const [enabled, setEnabled] = useState(true)

  useEffect(() => {
    let cancelled = false
    let freqSec = 60
    let timer = 0

    const load = async () => {
      try {
        const cfg = await api.config()
        setEnabled(cfg.adsEnabled)
        if (!cfg.adsEnabled || cancelled) return
        const res = await api.serveAd(placement)
        if (cancelled) return
        freqSec = res.frequencySeconds > 0 ? res.frequencySeconds : 60
        setAd(res)
      } catch {
        /* ad endpoints are best-effort; never block the app */
      }
    }

    const tick = () => {
      timer = window.setTimeout(() => {
        void load().then(() => {
          if (!cancelled) tick()
        })
      }, freqSec * 1000)
    }

    void load().then(() => {
      if (!cancelled) tick()
    })

    return () => {
      cancelled = true
      window.clearTimeout(timer)
      window.clearTimeout(shownRef.current.timer)
    }
  }, [placement])

  // Visibility-based genuine impression (>=1000ms visible).
  useEffect(() => {
    const el = boxRef.current
    if (!el || !ad?.adId) return
    const obs = new IntersectionObserver(
      (entries) => {
        const visible = entries.some((e) => e.isIntersecting)
        if (visible && !shownRef.current.ok) {
          shownRef.current.timer = window.setTimeout(() => {
            shownRef.current.ok = true
            api.recordImpression({
              adId: ad.adId!,
              placement,
              durationViewedMs: Math.max(ad.durationSeconds, 1) * 1000,
              clicked: false
            }).catch(() => {})
          }, 1000)
        }
      },
      { threshold: 0.6 }
    )
    obs.observe(el)
    return () => obs.disconnect()
  }, [ad, placement])

  useEffect(() => {
    shownRef.current = { ok: false, timer: 0 }
  }, [ad?.adId])

  if (!enabled || !ad?.adId || !ad.imageUrl) return null

  return (
    <div className="ad-slot" ref={boxRef}>
      <a
        href={ad.targetUrl || undefined}
        target="_blank"
        rel="noopener noreferrer sponsored"
        onClick={() =>
          api.recordImpression({ adId: ad.adId!, placement, durationViewedMs: 1000, clicked: true }).catch(() => {})
        }
      >
        <img src={ad.imageUrl} alt={ad.title || 'Advertisement'} loading="lazy" />
        <span className="ad-label">{ad.title ?? 'Sponsored'}</span>
      </a>
    </div>
  )
}