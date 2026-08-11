import { apiRequest } from './http'
import type { LocationFreshness } from '../freshness'

export type { LocationFreshness }

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
