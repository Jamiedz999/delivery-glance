export type LocationFreshness = 'LIVE' | 'DELAYED' | 'UNAVAILABLE'

const LIVE_LIMIT_SECONDS = 30
const USABLE_LIMIT_SECONDS = 120

/**
 * The three words every role sees. Typed rather than left as a string because the pages branch on
 * it — a marker is removed on Unavailable — and a comparison against a free string is a typo away
 * from a page that keeps showing a courier nobody has heard from.
 */
export type FreshnessLabel = 'Live' | 'Delayed' | 'Unavailable'

export interface FreshnessDescription {
  label: FreshnessLabel
  ageSeconds: number
  /** How long the server will still hold the position, which is what the countdown shows. */
  secondsUntilUnavailable: number
}

/**
 * Derived in the browser from the measurement time, so the page keeps ageing honestly between
 * requests instead of showing a freshness the server reported some time ago.
 *
 * <p>It lives outside any one role's API module because the Courier and the Recipient see the same
 * three words about the same reading, and two copies of thirty and one hundred and twenty seconds
 * would be two chances for a Courier to be told their position is live while the Recipient watching
 * them has already lost the marker.
 */
export function describeFreshness(recordedAt: string | null, now: number): FreshnessDescription | null {
  if (recordedAt === null) {
    return null
  }
  const ageSeconds = Math.max(0, Math.floor((now - Date.parse(recordedAt)) / 1000))
  const label: FreshnessLabel =
    ageSeconds <= LIVE_LIMIT_SECONDS ? 'Live' : ageSeconds <= USABLE_LIMIT_SECONDS ? 'Delayed' : 'Unavailable'
  return {
    label,
    ageSeconds,
    secondsUntilUnavailable: Math.max(0, USABLE_LIMIT_SECONDS - ageSeconds),
  }
}

export function formatCountdown(seconds: number): string {
  const minutes = Math.floor(seconds / 60)
  return `${minutes}:${String(seconds % 60).padStart(2, '0')}`
}
