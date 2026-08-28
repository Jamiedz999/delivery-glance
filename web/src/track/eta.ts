import type { EtaWindow } from './tracking'

/**
 * How long a window survives its last successful calculation before it is withdrawn. ADR 05 keeps a
 * window for at most five minutes with "last calculated X ago", then shows "temporarily unavailable"
 * — so past this age the browser drops the window itself, exactly as it ages the courier's position
 * out, without waiting for the server to send a fresh snapshot.
 */
export const ETA_STALE_LIMIT_SECONDS = 300

export interface EtaDescription {
  windowStart: string
  windowEnd: string
  /** How long since the estimate was last calculated. Drives "last calculated X ago". */
  ageSeconds: number
  /** Whether the page's clock has passed the window's upper bound. */
  runningLate: boolean
}

/**
 * Derives what to show for an ETA in the browser, from the window and the current time. Returns null
 * when there is nothing honest to show as a window — the server holds no current estimate, or the
 * last one has aged past the five-minute limit — which the page renders as "temporarily unavailable".
 * A non-null result still carries `runningLate`, because a missed window is not withdrawn: ADR 05 is
 * explicit that a late estimate stays visible rather than being silently erased.
 */
export function describeEta(eta: EtaWindow | null, now: number): EtaDescription | null {
  if (eta === null) {
    return null
  }
  const ageSeconds = Math.max(0, Math.floor((now - Date.parse(eta.calculatedAt)) / 1000))
  if (ageSeconds > ETA_STALE_LIMIT_SECONDS) {
    return null
  }
  return {
    windowStart: eta.windowStart,
    windowEnd: eta.windowEnd,
    ageSeconds,
    runningLate: now > Date.parse(eta.windowEnd),
  }
}
