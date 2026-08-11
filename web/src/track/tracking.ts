/**
 * What a Link Holder's browser reads, and the only request this application makes.
 *
 * It does not go through `api/http.ts`. That module is built around an Internal Account session —
 * CSRF echoing, problem codes a Dispatcher's page branches on — and none of it applies here: the
 * request is a GET authorized by the grant cookie, and every failure it can produce collapses to one
 * of the two below. Reusing it would import the shape of the internal API into the one page that
 * must not share anything with it.
 */

import type { DeliveryState } from '../api/deliveries'

/**
 * The same five states the internal application names, aliased rather than re-declared: they are
 * one server enum, and two copies of it would be two places to update when a sixth arrives. The
 * import above is type-only, so none of the internal API module reaches this bundle.
 */
export type RecipientState = DeliveryState

export interface Place {
  latitude: number
  longitude: number
}

export interface CourierPosition extends Place {
  accuracyMetres: number
  /** When the device measured it. The page ages this itself rather than trusting a label. */
  recordedAt: string
}

export interface TrackingMap {
  handoff: Place
  courier: CourierPosition | null
}

/**
 * Most fields are null in most states, because the server builds this for the state rather than
 * sending everything and expecting the page to hide what it should not show.
 */
export interface TrackingSnapshot {
  reference: string | null
  state: RecipientState
  handoffAddressLabel: string | null
  courierDisplayName: string | null
  map: TrackingMap | null
  completedAt: string | null
  deliveryTeamContact: string | null
}

/**
 * `unavailable` is the server's one refusal — unknown, expired, revoked, or a grant that has run
 * out — and is deliberately indistinguishable. `unreachable` is this browser failing to ask, which
 * says nothing about the link and must not be reported as if it did.
 */
export type TrackingResult =
  { status: 'ok'; snapshot: TrackingSnapshot } | { status: 'unavailable' } | { status: 'unreachable' }

export async function fetchSnapshot(): Promise<TrackingResult> {
  let response: Response
  try {
    response = await fetch('/api/tracking/snapshot', { credentials: 'same-origin' })
  } catch {
    return { status: 'unreachable' }
  }
  if (!response.ok) {
    return { status: 'unavailable' }
  }
  try {
    return { status: 'ok', snapshot: (await response.json()) as TrackingSnapshot }
  } catch {
    // A 200 whose body is not the snapshot is this page being unable to read its delivery, not the
    // link being gone. Saying "no longer available" would send the Recipient to the delivery team
    // over something a reload may well fix.
    return { status: 'unreachable' }
  }
}
