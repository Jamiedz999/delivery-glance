import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import type { CancellationReason, DeliveryDetail } from '../api/deliveries'
import { CANCELLATION_REASONS, DELIVERY_STATE_LABELS } from '../api/deliveries'
import { ApiError } from '../api/http'
import { useCancelDelivery, useDelivery } from '../api/queries'

export function DeliveryDetailPage() {
  const { id = '' } = useParams()
  const { data: delivery, isPending, isError, error } = useDelivery(id)

  if (isPending) {
    return <p role="status">Loading delivery…</p>
  }

  if (isError) {
    const notFound = error instanceof ApiError && error.status === 404
    return (
      <p role="alert">
        {notFound ? 'That delivery does not exist.' : 'Could not load this delivery. Reload the page to try again.'}
      </p>
    )
  }

  return (
    <section>
      <p>
        <Link to="/deliveries">Back to deliveries</Link>
      </p>
      <h1>{delivery.reference}</h1>
      <p>
        Status: <strong>{DELIVERY_STATE_LABELS[delivery.state]}</strong>
      </p>

      <h2>Route</h2>
      <dl>
        <dt>Pickup</dt>
        <dd>
          {delivery.pickup.addressLabel} ({delivery.pickup.latitude}, {delivery.pickup.longitude})
        </dd>
        <dt>Handoff</dt>
        <dd>
          {delivery.handoff.addressLabel} ({delivery.handoff.latitude}, {delivery.handoff.longitude})
        </dd>
      </dl>

      <h2>History</h2>
      <ol>
        {delivery.transitions.map((transition) => (
          <li key={`${transition.occurredAt}-${transition.nextState}`}>
            <time dateTime={transition.occurredAt}>
              {new Date(transition.occurredAt).toLocaleString()}
            </time>{' '}
            — {DELIVERY_STATE_LABELS[transition.nextState]} by {transition.actorDisplayName}
            {transition.reasonCode != null && ` (${labelFor(transition.reasonCode)})`}
            {transition.reasonNote != null && `: ${transition.reasonNote}`}
          </li>
        ))}
      </ol>

      {delivery.state === 'AWAITING_COURIER' ? (
        <CancelDeliveryForm delivery={delivery} />
      ) : (
        <p role="status">This delivery has reached a final state and cannot be changed.</p>
      )}
    </section>
  )
}

function CancelDeliveryForm({ delivery }: { delivery: DeliveryDetail }) {
  const cancel = useCancelDelivery(delivery.id)
  const [reason, setReason] = useState<CancellationReason>('NO_LONGER_REQUIRED')
  const [note, setNote] = useState('')
  // Held for the whole attempt so a retry after a failure repeats the same command rather than
  // issuing a new one.
  const [commandId] = useState(() => crypto.randomUUID())

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    cancel.mutate({
      commandId,
      expectedVersion: delivery.version,
      reason,
      note: note.trim() === '' ? null : note.trim(),
    })
  }

  const noteRequired = reason === 'OTHER'

  return (
    <form onSubmit={submit} noValidate>
      <h2>Cancel this delivery</h2>
      <p>A delivery can only be cancelled while it is still awaiting a courier.</p>

      {cancel.isError && (
        <p role="alert" className="error">
          {messageFor(cancel.error)}
        </p>
      )}

      <div className="field">
        <label htmlFor="cancellation-reason">Reason</label>
        <select
          id="cancellation-reason"
          value={reason}
          onChange={(event) => setReason(event.target.value as CancellationReason)}
        >
          {CANCELLATION_REASONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>

      <div className="field">
        <label htmlFor="cancellation-note">Note {noteRequired ? '(required)' : '(optional)'}</label>
        <textarea
          id="cancellation-note"
          value={note}
          onChange={(event) => setNote(event.target.value)}
          aria-required={noteRequired}
        />
      </div>

      <button type="submit" disabled={cancel.isPending} aria-busy={cancel.isPending}>
        {cancel.isPending ? 'Cancelling…' : 'Cancel delivery'}
      </button>
    </form>
  )
}

function labelFor(reason: CancellationReason): string {
  return CANCELLATION_REASONS.find((option) => option.value === reason)?.label ?? reason
}

function messageFor(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return 'Could not cancel the delivery. Try again in a moment.'
  }
  switch (error.code) {
    case 'delivery-version-conflict':
      return 'This delivery changed in another window. Reload the page and try again.'
    case 'delivery-invalid-transition':
      return 'This delivery can no longer be cancelled.'
    case 'invalid-request':
      return 'A note is required when the reason is “Other”.'
    case 'delivery-not-found':
      return 'That delivery does not exist.'
    default:
      return 'Could not cancel the delivery. Try again in a moment.'
  }
}
