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

export const UNAVAILABLE_LINK =
  'This tracking link is no longer available. Contact the delivery team that shared it.'

export const UNREACHABLE = 'Could not reach the delivery service. Check your connection and reload.'

export const MAP_UNAVAILABLE = 'The map is unavailable, so the courier’s position is described below instead.'

/** A whole time, in the reader's own locale. Core has no time-zone lookup for the handoff address. */
export function formatTime(instant: string): string {
  return new Date(instant).toLocaleString()
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
