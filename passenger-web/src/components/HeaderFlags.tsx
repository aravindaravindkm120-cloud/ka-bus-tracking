import { memo, useEffect, useRef, useState } from 'react'

const FLAGS_SRC = `${import.meta.env.BASE_URL}india-karnataka-flags-5s.mp4`

/** Static India + Karnataka flag visual used when video playback is unavailable. */
const FlagsFallback = (
  <svg
    className="flags-fallback"
    viewBox="0 0 64 36"
    role="img"
    aria-label="India and Karnataka flags"
    focusable="false"
  >
    <rect x="0" y="0" width="32" height="36" fill="#ff9933" />
    <rect x="0" y="12" width="32" height="12" fill="#ffffff" />
    <circle cx="16" cy="18" r="4.5" fill="#000080" />
    <rect x="0" y="24" width="32" height="12" fill="#138808" />
    <rect x="32" y="0" width="32" height="18" fill="#ff0000" />
    <rect x="32" y="18" width="32" height="18" fill="#ffcc00" />
  </svg>
)

/**
 * Compact India + Karnataka flag video shown in the passenger header.
 *
 * Tapping it opens a centred, 16:9 modal/lightbox. The single video asset is
 * mounted either small (header) or expanded (modal) — never two at once — and
 * the play position is carried across the swap. Purely local state: GPS,
 * WebSocket, bus, map and search updates never re-render, restart or close it.
 */
const HeaderFlags = memo(function HeaderFlags() {
  const videoRef = useRef<HTMLVideoElement>(null)
  const openerRef = useRef<HTMLButtonElement>(null)
  const closeRef = useRef<HTMLButtonElement>(null)
  const positionRef = useRef(0)
  const [failed, setFailed] = useState(false)
  const [expanded, setExpanded] = useState(false)

  // Attaches time tracking + position restore to whichever video is mounted
  // (small header or modal), and starts it quietly when appropriate.
  useEffect(() => {
    const v = videoRef.current
    if (!v) return
    const onTime = () => {
      positionRef.current = v.currentTime
    }
    v.addEventListener('timeupdate', onTime)

    const begin = () => {
      try {
        v.currentTime = positionRef.current
      } catch {
        /* index out of range is harmless */
      }
      if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return
      const p = v.play()
      if (p) p.catch(() => {})
    }
    if (v.readyState >= 1) begin()
    else v.addEventListener('loadedmetadata', begin, { once: true })

    return () => v.removeEventListener('timeupdate', onTime)
  }, [expanded, failed])

  // ESC closes the modal; focus the close button inside the dialog.
  useEffect(() => {
    if (!expanded) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') close()
    }
    window.addEventListener('keydown', onKey)
    closeRef.current?.focus()
    return () => window.removeEventListener('keydown', onKey)
  }, [expanded])

  const open = () => {
    if (videoRef.current) positionRef.current = videoRef.current.currentTime
    setExpanded(true)
  }

  const close = () => {
    if (videoRef.current) positionRef.current = videoRef.current.currentTime
    setExpanded(false)
    openerRef.current?.focus()
  }

  return (
    <>
      <button
        ref={openerRef}
        type="button"
        className="header-flags"
        onClick={expanded ? undefined : open}
        aria-label="Open India and Karnataka flag video"
        aria-haspopup="dialog"
        aria-expanded={expanded}
        title="Play India + Karnataka flags"
      >
        {expanded || failed ? (
          <span className="flag-gap" aria-hidden="true" />
        ) : (
          <video
            ref={videoRef}
            className="header-flags-video"
            src={FLAGS_SRC}
            muted
            loop
            playsInline
            preload="auto"
            disablePictureInPicture
            tabIndex={-1}
            aria-hidden="true"
            onError={() => setFailed(true)}
          />
        )}
      </button>

      {expanded && (
        <div
          className="flag-overlay"
          role="dialog"
          aria-modal="true"
          aria-label="India and Karnataka flag video"
          onClick={(e) => {
            if (e.target === e.currentTarget) close()
          }}
        >
          <div className="flag-modal">
            <button
              ref={closeRef}
              type="button"
              className="flag-close"
              onClick={close}
              aria-label="Close flag video"
            >
              ✕
            </button>
            <p className="flag-title">INDIA + KARNATAKA FLAGS</p>
            <div className="flag-video-wrap" onClick={close}>
              {failed ? (
                FlagsFallback
              ) : (
                <video
                  ref={videoRef}
                  className="flag-video-large"
                  src={FLAGS_SRC}
                  muted
                  loop
                  playsInline
                  preload="auto"
                  disablePictureInPicture
                  aria-label="India and Karnataka flags"
                  onError={() => setFailed(true)}
                />
              )}
            </div>
            <p className="flag-hint">Tap the video or press Esc to close</p>
          </div>
        </div>
      )}
    </>
  )
})

export default HeaderFlags