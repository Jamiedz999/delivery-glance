import { apiRequest } from './http'

export type LocationFreshness = 'LIVE' | 'DELAYED' | 'UNAVAILABLE'

export type ReportOutcome =
  'ACCEPTED' | 'REJECTED_LOW_ACCURACY' | 'REJECTED_FUTURE_DATED' | 'REJECTED_STALE' | 'REJECTED_NOT_NEWER'

/** Never carries coordinates: the browser already knows where it is. */
export interface CourierLocation {
  freshness: LocationFreshness
  recordedAt: string | null
  accuracyMetres: number | null
}

export interface Courier {
  displayName: string
  onDuty: boolean
  onDutyChangedAt: string | null
  /** The session the server currently accepts reports for, which a reloaded page cannot use. */
  sharing: { startedAt: string } | null
  location: CourierLocation
}

/** The one response that carries the reporting secret; it is not readable again. */
export interface StartedSession {
  generation: string
  reportingSecret: string
  startedAt: string
}

export interface LocationReportInput {
  generation: string
  reportingSecret: string
  longitude: number
  latitude: number
  accuracyMetres: number
  recordedAt: string
}

export interface LocationReportResult {
  outcome: ReportOutcome
  location: CourierLocation
}

const LIVE_LIMIT_SECONDS = 30
const USABLE_LIMIT_SECONDS = 120

export function fetchCourier(): Promise<Courier> {
  return apiRequest<Courier>('/api/couriers/me')
}

export function setDuty(onDuty: boolean): Promise<Courier> {
  return apiRequest<Courier>('/api/couriers/me/duty', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ onDuty }),
  })
}

export function startLocationSharing(): Promise<StartedSession> {
  return apiRequest<StartedSession>('/api/couriers/me/location-sharing', { method: 'POST' })
}

export function stopLocationSharing(): Promise<void> {
  return apiRequest<void>('/api/couriers/me/location-sharing', { method: 'DELETE' })
}

export function reportLocation(input: LocationReportInput): Promise<LocationReportResult> {
  return apiRequest<LocationReportResult>('/api/couriers/me/location-reports', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export interface FreshnessDescription {
  label: string
  ageSeconds: number
  /** How long the server will still hold the position, which is what the countdown shows. */
  secondsUntilUnavailable: number
}

/**
 * Derived in the browser from the measurement time, so the page keeps ageing honestly between
 * requests instead of showing a freshness the server reported some time ago.
 */
export function describeFreshness(recordedAt: string | null, now: number): FreshnessDescription | null {
  if (recordedAt === null) {
    return null
  }
  const ageSeconds = Math.max(0, Math.floor((now - Date.parse(recordedAt)) / 1000))
  const label =
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
