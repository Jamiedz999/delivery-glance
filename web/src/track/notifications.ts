/**
 * The Recipient's opt-in for off-band notification, from the tracking page.
 *
 * Like `tracking.ts`, it does not go through `api/http.ts`: that module is built for an Internal
 * Account session, and this is a Link Holder acting under the grant cookie. What it does share with
 * the internal client is the one thing every unsafe request to this origin needs — echoing the CSRF
 * cookie as a header — because these POST and DELETE carry the grant ambiently and must prove they
 * were meant.
 */

export type NotificationChannel = 'EMAIL' | 'SMS'

export interface NotificationSubscription {
  channel: NotificationChannel
  target: string
  /** False once revoked: the row is kept so the page can show it was turned off, not forgotten. */
  active: boolean
}

/**
 * The opt-in section's whole state. `available` is whether this deployment can notify at all; when
 * it is false the page offers no control, because a form that led nowhere would be a promise the
 * deployment cannot keep.
 */
export interface OptInState {
  available: boolean
  subscription: NotificationSubscription | null
}

/**
 * `invalid` is the server rejecting the target as not matching its channel — a Recipient mistake to
 * show inline. `unavailable` is the feature being off. `error` is anything else, including this
 * browser failing to ask; the page keeps its current state and says nothing about the link.
 */
export type SubscribeResult =
  | { status: 'ok'; subscription: NotificationSubscription }
  | { status: 'invalid' }
  | { status: 'unavailable' }
  | { status: 'error' }

const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'
const ENDPOINT = '/api/tracking/notifications'

export async function fetchOptInState(): Promise<OptInState> {
  try {
    const response = await fetch(ENDPOINT, { credentials: 'same-origin' })
    if (!response.ok) {
      return { available: false, subscription: null }
    }
    return (await response.json()) as OptInState
  } catch {
    // Unreachable is treated as "not available": with no answer, offering the control would be a
    // guess, and the page has a delivery to show regardless.
    return { available: false, subscription: null }
  }
}

export async function subscribe(channel: NotificationChannel, target: string): Promise<SubscribeResult> {
  let response: Response
  try {
    response = await fetch(ENDPOINT, {
      method: 'POST',
      credentials: 'same-origin',
      headers: unsafeHeaders(),
      body: JSON.stringify({ channel, target }),
    })
  } catch {
    return { status: 'error' }
  }
  if (response.ok) {
    return { status: 'ok', subscription: (await response.json()) as NotificationSubscription }
  }
  if (response.status === 422) {
    return { status: 'invalid' }
  }
  if (response.status === 503) {
    return { status: 'unavailable' }
  }
  return { status: 'error' }
}

export async function revoke(): Promise<boolean> {
  try {
    const response = await fetch(ENDPOINT, {
      method: 'DELETE',
      credentials: 'same-origin',
      headers: unsafeHeaders(),
    })
    return response.ok
  } catch {
    return false
  }
}

function unsafeHeaders(): Record<string, string> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  const token = readCookie(CSRF_COOKIE)
  if (token !== undefined) {
    headers[CSRF_HEADER] = token
  }
  return headers
}

function readCookie(name: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((entry) => entry.startsWith(`${name}=`))
    ?.slice(name.length + 1)
}
