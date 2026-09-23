# Ad revenue — honest, first-party, ¥0-cost

The requirement was to generate revenue **without fake impressions/clicks**.
The design is a closed-loop first-party ad system:

- No Google AdMob / Meta / 3rd-party SDK anywhere.
- The operator sells placements directly to local Karnataka businesses
  (transport crews can call these local advertisers themselves).
- Every impression a client reports must first be genuinely visible.

## How it works

**Serve**
```
GET /api/public/ad/serve?placement=PASSENGER_WEB_HOME     → ServeAdResponse
GET /api/crew/ad                                           → CREW_APP_HOME slot
```
The server picks the highest-priority **active** campaign for the placement
(window `start_at`→`end_at`, `enabled=true`, under `max_impressions`). Ad ids
are per-serve UUIDs; no creative is ever inset into tracking controls.

**Render (client-side, non-overlapping)**
- Passenger web: small bottom banner / bus-details card. AdSlot.tsx uses an
  `IntersectionObserver`; an impression is reported only after ~1 s of the ad
  being ≥60% on screen.
- Admin app: dashboard / non-critical regions only.
- Crew app: home screen slot strictly **below** Start/End GPS controls; the
  service flow never runs ads over the map/GPS area.

**Report (server-validated)**
```
POST /api/public/ad/impression {adId, placement, deviceId, durationViewedMs, clicked}
```
The server:
- rejects unknown/expired `adId`, mismatched placement, nonsense `duration`:
- **records the impression only when `durationViewedMs > 0`**
  (`AdImpressionRepository.countByCampaignIdAndDurationViewedMsGreaterThan`).

So billing/campaign stats count only genuine views, and the code path for fake
clicks/impressions was deliberately deleted (see FINAL_REPORT: "no fake ad").

## Slots & pacing (seed `ad_placements`)

| Placement | Default duration | Repeat frequency | Where |
|---|---|---|---|
| PASSENGER_WEB_HOME | 5 s | 180 s | passenger hero/banner |
| PASSENGER_WEB_SEARCH | 5 s | 180 s | beside results |
| PASSENGER_WEB_BUS_DETAILS | 5 s | 180 s | detail card |
| PASSENGER_WEB_MAP_BOTTOM | 5 s | 180 s | map bottom card |
| ADMIN_APP_DASHBOARD | 5 s | 300 s | dashboard |
| ADMIN_APP_NON_CRITICAL | 5 s | 300 s | lists, empty states |
| CREW_APP_HOME | 5 s | 600 s | home only, never over GPS controls |
| CREW_APP_NON_CRITICAL | 5 s | 600 s | secondary regions |

Every placement is configurable server-side (enable/disable, duration,
frequency, priority, max impressions). Ad management is **SUPER_ADMIN-only**.

## Metrics that are genuine

- `impressions` = rows where `durationViewedMs > 0`
- `clicks` = `clicked=true`
- `CTR = clicks / impressions`
- `CampaignStats` endpoint returns exactly these three; impossible to inflate
  without the client showing the ad.

## What a zero-budget launch looks like

1. Seed local advertisers manually (bus depots, garages, Darshini hotels,
   auto repair, mobile stores along routes).
2. Upload creative images (any public URL — Coil/`<img>` render them; a future
   storage bucket is optional).
3. Create campaigns at 0 cost via `POST /api/admin/ads/campaigns`.

## Honesty hard-rules (encoded in code)

- No ad is ever drawn over GPS / Start / End / assignment controls.
- No impression count is incremented by network call alone; it must be a real
  visibility window (`durationViewedMs > 0`).
- De-dupe by `adId`; unknown serve-ids get 404-ish rejections server-side.