import type { TrackingConnection } from './updates'
import type { RecipientState } from './tracking'

/**
 * Every sentence the Recipient reads about state, derived from the state and nothing else.
 *
 * ADR 05 fixes both halves: the headline is the public outcome, and the next step names the next
 * externally meaningful action. Neither may leak dispatch activity — no matching, no declines, no
 * reassignment — and the cancelled step in particular must not imply a retry nobody has arranged.
 */
export interface StateCopy {
  headline: string
  nextStep: string
}

export const STATE_COPY: Record<RecipientState, StateCopy> = {
  AWAITING_COURIER: {
    headline: 'We’re preparing your delivery',
    nextStep: 'A courier is being arranged to collect it.',
  },
  ASSIGNED: {
    headline: 'A courier has been assigned',
    nextStep: 'They are on their way to collect your delivery.',
  },
  IN_TRANSIT: {
    headline: 'Your delivery is on the way',
    nextStep: 'The courier is heading to the handoff address.',
  },
  DELIVERED: {
    headline: 'Delivered',
    nextStep: 'Your delivery was handed over.',
  },
  CANCELLED: {
    headline: 'This delivery was cancelled',
    nextStep: 'Nothing further is scheduled for it.',
  },
}

/**
 * CONTEXT's **Tracking Connection**: whether this page is still hearing about changes.
 *
 * It is a different question from how old the courier's position is, and is said in different words
 * for that reason. A reader who cannot tell the two apart will read a stale marker as a lost
 * connection, or worse, read a live connection as a promise that the marker is current.
 *
 * None of these name a cause. A Recipient can do nothing about any of them except reload, so the
 * only line that asks for anything is the one where reloading is the answer.
 */
export const TRACKING_CONNECTION_COPY: Record<TrackingConnection, string> = {
  connecting: 'Connecting for updates…',
  live: 'Updating automatically',
  reconnecting: 'Reconnecting for updates…',
  off: 'Not updating automatically — reload to check for changes.',
}

export const UNAVAILABLE_LINK =
  'This tracking link is no longer available. Contact the delivery team that shared it.'

export const UNREACHABLE = 'Could not reach the delivery service. Check your connection and reload.'

export const MAP_UNAVAILABLE = 'The map is unavailable, so the courier’s position is described below instead.'

/**
 * Said when the server holds no usable position at all. It names the state and not a cause, because
 * three different causes arrive here as the same absence — the Courier never started sharing, they
 * pressed Stop, or their last reading aged past the point where the server keeps coordinates — and
 * the response cannot tell them apart. Asserting one of them would be a guess presented as fact.
 */
export const NO_POSITION = 'The courier’s position is not available right now.'

/**
 * Shown while Assigned or In Transit when no current window exists — the last estimate failed or the
 * courier's location is not usable. It names no cause, because several arrive here as the same
 * absence, and asserting one would be a guess presented as fact.
 */
export const ETA_UNAVAILABLE = 'Estimated arrival is temporarily unavailable.'

/**
 * Shown once the page's clock passes the window's upper bound. ADR 05 keeps the missed window on
 * screen and says this alongside it rather than silently replacing it, so a Recipient is never shown
 * a moved estimate as though the first had never been given.
 */
export const ETA_RUNNING_LATE = 'Running later than expected — updating the estimate.'

/**
 * Shown on the window that replaces a missed one. ADR 05 requires a replacement to be labelled rather
 * than swapped in silently, so a Recipient who was told the delivery was running late sees that the
 * new window is a revised estimate, not the one they were originally given.
 */
export const ETA_UPDATED = 'Estimated arrival updated.'

/** A whole time, in the reader's own locale. Core has no time-zone lookup for the handoff address. */
export function formatTime(instant: string): string {
  return new Date(instant).toLocaleString()
}

/** Just the clock time, in the reader's own locale — the two ends of an ETA Window read as times. */
export function formatClockTime(instant: string): string {
  return new Date(instant).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })
}

export function formatAge(ageSeconds: number): string {
  if (ageSeconds < 5) {
    return 'just now'
  }
  if (ageSeconds < 60) {
    return `${ageSeconds} seconds ago`
  }
  const minutes = Math.floor(ageSeconds / 60)
  return minutes === 1 ? 'a minute ago' : `${minutes} minutes ago`
}
