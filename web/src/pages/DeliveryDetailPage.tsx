import { useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import type { Address, CancellationReason, DeliveryDetail, DeliveryState } from '../api/deliveries'
import { CANCELLATION_REASONS, DELIVERY_STATE_LABELS, isTerminalState } from '../api/deliveries'
import { ApiError } from '../api/http'
import {
  useAssignCourier,
  useCancelDelivery,
  useCopyTrackingLink,
  useCourierRecommendation,
  useDelivery,
} from '../api/queries'

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
        {notFound
          ? 'That delivery does not exist.'
          : 'Could not load this delivery. Reload the page to try again.'}
      </p>
    )
  }

  return (
    <section className="detail">
      <p>
        <Link to="/deliveries">← Back to deliveries</Link>
      </p>

      <header className="detail-head">
        <div>
          <h1>{delivery.reference}</h1>
          {delivery.assignment !== null && (
            <p className="detail-sub">
              Assigned to <strong>{delivery.assignment.courierDisplayName}</strong>
            </p>
          )}
        </div>
        <span className={`status-chip ${statusChipClass(delivery.state)}`}>
          {DELIVERY_STATE_LABELS[delivery.state]}
        </span>
      </header>

      <div className="detail-grid">
        <div className="detail-col">
          <section className="card" aria-labelledby="delivery-heading">
            <h2 id="delivery-heading" className="card-title">
              Delivery
            </h2>
            <dl className="address-pair">
              <AddressPoint label="Handoff" tag="public" address={delivery.handoff} />
              <AddressPoint label="Pickup" tag="internal" address={delivery.pickup} />
            </dl>
          </section>

          <DirectAssignment delivery={delivery} />

          {(delivery.state === 'AWAITING_COURIER' || delivery.state === 'ASSIGNED') && (
            <CancelDeliveryForm delivery={delivery} />
          )}
          {isTerminalState(delivery.state) && (
            <p className="card detail-note" role="status">
              This delivery has reached a final state and cannot be changed.
            </p>
          )}
          {delivery.state === 'IN_TRANSIT' && (
            <p className="card detail-note" role="status">
              The Courier has confirmed pickup; only they can confirm handoff.
            </p>
          )}
        </div>

        <aside className="detail-col">
          <Timeline delivery={delivery} />
          <TrackingLinkPanel delivery={delivery} />
        </aside>
      </div>
    </section>
  )
}

function AddressPoint({
  label,
  tag,
  address,
}: {
  label: string
  tag: 'public' | 'internal'
  address: Address
}) {
  return (
    <div className="address-point">
      <dt>
        {label}
        <span className={`address-tag is-${tag}`}>{tag}</span>
      </dt>
      <dd>
        {address.addressLabel} ({address.latitude}, {address.longitude})
      </dd>
    </div>
  )
}

function Timeline({ delivery }: { delivery: DeliveryDetail }) {
  return (
    <section className="card" aria-labelledby="history-heading">
      <h2 id="history-heading" className="card-title">
        History
      </h2>
      <ol className="timeline">
        {delivery.transitions.map((transition) => (
          <li key={`${transition.occurredAt}-${transition.nextState}`}>
            <span className="timeline-dot" aria-hidden="true" />
            <div className="timeline-body">
              <span className="timeline-event">
                {DELIVERY_STATE_LABELS[transition.nextState]} by {transition.actorDisplayName}
                {transition.reasonCode != null && ` (${labelFor(transition.reasonCode)})`}
                {transition.reasonNote != null && `: ${transition.reasonNote}`}
              </span>
              <time className="timeline-time" dateTime={transition.occurredAt}>
                {new Date(transition.occurredAt).toLocaleString()}
              </time>
            </div>
          </li>
        ))}
      </ol>
    </section>
  )
}

/**
 * The Recipient Tracking Link, and the Dispatcher's one control over it: Copy.
 *
 * Per its lifecycle the link is valid from Delivery creation and independent of the Delivery's own
 * state, and the contract carries no per-link status or expiry timestamp — so the status line shows
 * the documented policy rather than a fetched value. Copy is the exception: it exchanges the Delivery
 * for a raw capability URL, which `useCopyTrackingLink` writes straight to the clipboard and never
 * hands back. Only the expiry the response carries reaches this component, and it is shown relative.
 *
 * The control is offered only while the Delivery is non-terminal; a completed or cancelled Delivery
 * has nothing a Dispatcher would still be sharing, so the button is absent rather than disabled.
 */
function TrackingLinkPanel({ delivery }: { delivery: DeliveryDetail }) {
  const copy = useCopyTrackingLink(delivery.id)
  const isTerminal = isTerminalState(delivery.state)

  return (
    <section className="card" aria-labelledby="tracking-heading">
      <h2 id="tracking-heading" className="card-title">
        Tracking link
      </h2>
      <p className="tracking-status">
        <span className="status-dot is-live" aria-hidden="true" />
        Active for the Recipient
      </p>
      <p className="tracking-note">
        A private, read-only link the Recipient opens without an account. It expires 24 hours after the
        Delivery is completed.
      </p>
      {!isTerminal && (
        <div className="tracking-actions">
          <button
            type="button"
            className="btn-secondary btn-sm"
            onClick={() => copy.mutate()}
            disabled={copy.isPending}
            aria-busy={copy.isPending}
          >
            {copy.isPending ? 'Copying…' : 'Copy tracking link'}
          </button>
          {copy.isSuccess && (
            <p className="tracking-copied" role="status">
              Copied · expires {formatExpiry(copy.data.expiresAt)}
            </p>
          )}
          {copy.isError && (
            <p className="tracking-copied error" role="alert">
              Could not copy the link. Try again.
            </p>
          )}
        </div>
      )}
    </section>
  )
}

/**
 * The response's expiry as a relative phrase, e.g. `in 6 days`. Rounded to the coarsest unit that
 * still reads truthfully, because the Dispatcher is being told roughly how long the link they just
 * copied will keep working, not an exact instant.
 */
function formatExpiry(expiresAt: string): string {
  const deltaMs = new Date(expiresAt).getTime() - Date.now()
  const relative = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' })
  const days = Math.round(deltaMs / 86_400_000)
  if (Math.abs(days) >= 1) {
    return relative.format(days, 'day')
  }
  const hours = Math.round(deltaMs / 3_600_000)
  if (Math.abs(hours) >= 1) {
    return relative.format(hours, 'hour')
  }
  return relative.format(Math.round(deltaMs / 60_000), 'minute')
}

function statusChipClass(state: DeliveryState): string {
  switch (state) {
    case 'AWAITING_COURIER':
      return 'is-awaiting'
    case 'ASSIGNED':
      return 'is-assigned'
    case 'IN_TRANSIT':
      return 'is-transit'
    case 'DELIVERED':
      return 'is-delivered'
    case 'CANCELLED':
      return 'is-cancelled'
  }
}

/**
 * Direct Assignment, and — outliving it — whatever it has to say about a press that was refused.
 *
 * The two are separated because the shortlist is only meaningful while a Delivery is still Awaiting
 * a Courier, and a refusal is most meaningful exactly when it is not. Losing a race refuses the
 * command *and* means the next read finds somebody else's Assignment, so a message rendered inside
 * the shortlist would be destroyed by the refetch its own failure triggered — leaving the page
 * redrawn as an assigned Delivery, with the winner's Courier sitting where a success would have put
 * one and nothing at all to say the press did not do it.
 */
function DirectAssignment({ delivery }: { delivery: DeliveryDetail }) {
  const assign = useAssignCourier(delivery.id)
  const commandIds = useRef(new Map<string, string>())

  function directAssign(courierId: string) {
    let commandId = commandIds.current.get(courierId)
    if (commandId === undefined) {
      commandId = crypto.randomUUID()
      commandIds.current.set(courierId, commandId)
    }
    assign.mutate({ courierId, expectedVersion: delivery.version, commandId })
  }

  return (
    <>
      {assign.isError && (
        <p role="alert" className="error card detail-note">
          {assignmentMessageFor(assign.error)}
        </p>
      )}
      {delivery.state === 'AWAITING_COURIER' && (
        <RecommendationPanel delivery={delivery} assigning={assign.isPending} onAssign={directAssign} />
      )}
    </>
  )
}

interface RecommendationPanelProps {
  delivery: DeliveryDetail
  assigning: boolean
  onAssign: (courierId: string) => void
}

function RecommendationPanel({ delivery, assigning, onAssign }: RecommendationPanelProps) {
  const recommendation = useCourierRecommendation(delivery.id)

  return (
    <section className="card" aria-labelledby="recommendation-heading">
      <div className="card-head">
        <h2 id="recommendation-heading" className="card-title">
          Nearest eligible Couriers
        </h2>
        {recommendation.data !== undefined && (
          <button
            type="button"
            className="btn-ghost btn-sm"
            onClick={() => void recommendation.refetch()}
            disabled={recommendation.isFetching || assigning}
            aria-busy={recommendation.isFetching}
          >
            Refresh recommendation
          </button>
        )}
      </div>
      {recommendation.isPending && <p role="status">Calculating a fresh recommendation…</p>}
      {recommendation.isError && <p role="alert">Could not calculate a recommendation. Try again.</p>}
      {recommendation.data !== undefined && (
        <>
          <p className="card-meta">
            Calculated{' '}
            <time dateTime={recommendation.data.calculatedAt}>
              {new Date(recommendation.data.calculatedAt).toLocaleString()}
            </time>
          </p>
          {recommendation.data.candidates.length === 0 ? (
            <p className="card-meta">
              No Courier is currently eligible. Refresh when duty or location changes.
            </p>
          ) : (
            <ol className="candidate-list">
              {recommendation.data.candidates.map((candidate, index) => (
                <li key={candidate.courierId} className="candidate-card">
                  <span className="candidate-rank" aria-hidden="true">
                    {index + 1}
                  </span>
                  <div className="candidate-body">
                    <span className="candidate-name">{candidate.displayName}</span>
                    <span className="candidate-meta">
                      {formatDistance(candidate.distanceMetres)} from pickup
                    </span>
                  </div>
                  <button
                    type="button"
                    className="btn-primary btn-sm"
                    aria-label={`Direct assign ${candidate.displayName}`}
                    onClick={() => onAssign(candidate.courierId)}
                    disabled={assigning}
                    aria-busy={assigning}
                  >
                    Direct assign
                  </button>
                </li>
              ))}
            </ol>
          )}
        </>
      )}
    </section>
  )
}

/** The candidate DTO carries metres; the Dispatcher reads kilometres to one decimal, e.g. `0.4 km`. */
function formatDistance(distanceMetres: number): string {
  return `${(distanceMetres / 1000).toFixed(1)} km`
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
    <form className="card" onSubmit={submit} noValidate>
      <h2 className="card-title">Cancel this delivery</h2>
      <p className="card-meta">
        A delivery can be cancelled before pickup, including after a Courier has been assigned.
      </p>

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

function assignmentMessageFor(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return 'Could not assign the Courier. Try again in a moment.'
  }
  switch (error.code) {
    case 'courier-not-eligible':
      return 'That Courier is no longer eligible. Refresh the recommendation.'
    case 'assignment-delivery-changed':
    case 'assignment-conflict':
      return 'Another assignment changed this Delivery. Reload it to see the winner.'
    default:
      return 'Could not assign the Courier. Try again in a moment.'
  }
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
