// Returns a stable per-browser device id used only by the backend to keep
// favorites + recent searches. No PII, purely generated client-side.
export function deviceId(): string {
  const KEY = 'kabus-device-id'
  let id = localStorage.getItem(KEY)
  if (!id) {
    id = crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2)}`
    localStorage.setItem(KEY, id)
  }
  return id
}