import { useEffect, useRef, useState } from 'react'
import { describeFreshness, formatCountdown } from '../freshness'
import type { CourierDelivery } from '../api/deliveries'
import { ApiError } from '../api/http'
import { useCourier, useCurrentCourierDelivery, useProgressCourierDelivery, useSetDuty } from '../api/queries'
import { SHARING_STATUS_LABELS, useLocationSharing } from './useLocationSharing'
import type { SharingStatus } from './useLocationSharing'

export function CourierHomePage() {
  const { data: courier, isPending, isError } = useCourier()
  const duty = useSetDuty()
  const sharing = useLocationSharing()
  const freshness = describeFreshness(courier?.location.recordedAt ?? null, useTicker())

  if (isPending) {
    return <p role="status">Loading your workspace…</p>
  }
  if (isError || courier === undefined) {
    return <p role="alert">Could not load your workspace. Reload the page to try again.</p>
  }

  const leftoverSession = courier.sharing !== null && sharing.status === 'OFF'

  return (
    <section className="courier">
      <header className="courier-head">
        <span className="courier-avatar" aria-hidden="true">
          {initialsOf(courier.displayName)}
        </span>
        <h1 className="courier-title">Courier workspace</h1>
      </header>

      <div className="courier-controls">
        <section className="card courier-card" aria-labelledby="duty-heading">
          <h2 id="duty-heading" className="card-title">
            Duty
          </h2>
          <p className="courier-state" role="status">
            <span className={`status-dot ${courier.onDuty ? 'is-on' : 'is-off'}`} aria-hidden="true" />
            {courier.onDuty ? 'On duty' : 'Off duty'}
          </p>
          {courier.onDutyChangedAt !== null && (
            <p className="card-meta">
              Since{' '}
              <time dateTime={courier.onDutyChangedAt}>
                {new Date(courier.onDutyChangedAt).toLocaleString()}
              </time>
            </p>
          )}
          <div className="courier-actions">
            <button
              type="button"
              className={courier.onDuty ? 'btn-secondary' : 'btn-primary'}
              onClick={() => duty.mutate(!courier.onDuty)}
              disabled={duty.isPending}
              aria-busy={duty.isPending}
            >
              {courier.onDuty ? 'Go off duty' : 'Go on duty'}
            </button>
          </div>
          {duty.isError && (
            <p role="alert" className="error">
              Your duty change was not saved. Try again.
            </p>
          )}
          <p className="courier-note">
            Going on duty does not share your location. That is a separate decision.
          </p>
        </section>

        <section className="card courier-card" aria-labelledby="sharing-heading">
          <h2 id="sharing-heading" className="card-title">
            Location sharing
          </h2>
          <p className="courier-state" role="status">
            <span className={`status-dot ${sharingDotClass(sharing.status)}`} aria-hidden="true" />
            {SHARING_STATUS_LABELS[sharing.status]}
          </p>
          {sharing.notice !== null && (
            <p role="alert" className="error">
              {sharing.notice}
            </p>
          )}

          <p className="courier-note">
            Your position is only reported while this page is open and in front of you. Reloading, signing out
            or pressing Stop ends it, and the server forgets where you are.
          </p>

          {leftoverSession && (
            <p className="courier-note">
              An earlier page started sharing at{' '}
              <time dateTime={courier.sharing?.startedAt}>
                {new Date(courier.sharing?.startedAt ?? '').toLocaleTimeString()}
              </time>
              . This page holds no reporting secret for it, so it can only replace that session or end it.
            </p>
          )}

          <div className="courier-actions">
            {sharing.status === 'OFF' && (
              <button
                type="button"
                className="btn-primary"
                onClick={() => void sharing.start()}
                disabled={sharing.busy}
                aria-busy={sharing.busy}
              >
                Start sharing
              </button>
            )}
            {/* Offered for a session this page did not start too, so a reload cannot strand one. */}
            {(sharing.status !== 'OFF' || leftoverSession) && (
              <button
                type="button"
                className="btn-secondary"
                onClick={() => void sharing.stop()}
                disabled={sharing.busy}
                aria-busy={sharing.busy}
              >
                Stop sharing
              </button>
            )}
          </div>

          <div className="courier-position">
            <h3 className="courier-position-label">Position held by the server</h3>
            {freshness === null || freshness.label === 'Unavailable' ? (
              <p className="courier-position-line">
                Unavailable — the server holds no usable position for you.
              </p>
            ) : (
              <p className="courier-position-line">
                {freshness.label} — measured {freshness.ageSeconds} seconds ago, removed in{' '}
                {formatCountdown(freshness.secondsUntilUnavailable)}
                {courier.location.accuracyMetres !== null &&
                  `, accurate to about ${Math.round(courier.location.accuracyMetres)} metres`}
              </p>
            )}
          </div>
        </section>
      </div>

      <CurrentDeliverySection />
    </section>
  )
}

function CurrentDeliverySection() {
  const current = useCurrentCourierDelivery()
  const pickup = useProgressCourierDelivery('pickup')
  const handoff = useProgressCourierDelivery('handoff')
  const commandIds = useRef(new Map<string, string>())

  function progress(delivery: CourierDelivery, action: 'pickup' | 'handoff') {
    const key = `${delivery.id}:${action}`
    let commandId = commandIds.current.get(key)
    if (commandId === undefined) {
      commandId = crypto.randomUUID()
      commandIds.current.set(key, commandId)
    }
    const mutation = action === 'pickup' ? pickup : handoff
    mutation.mutate({
      deliveryId: delivery.id,
      input: { commandId, expectedVersion: delivery.version },
    })
  }

  const delivery = current.data ?? null
  // One reading of the state drives all four decisions, so they cannot drift apart.
  const step =
    delivery?.state === 'ASSIGNED'
      ? {
          action: 'pickup' as const,
          mutation: pickup,
          status: 'Assigned — pickup not yet confirmed',
          chip: 'is-assigned',
          label: 'Confirm pickup',
        }
      : {
          action: 'handoff' as const,
          mutation: handoff,
          status: 'In transit',
          chip: 'is-transit',
          label: 'Confirm handoff',
        }

  return (
    <section className="card courier-delivery" aria-labelledby="current-delivery-heading">
      <div className="card-head">
        <h2 id="current-delivery-heading" className="card-title">
          Current Delivery
        </h2>
        {delivery !== null && <span className={`status-chip ${step.chip}`}>{step.status}</span>}
      </div>
      {current.isPending && <p role="status">Loading your current Delivery…</p>}
      {current.isError && (
        <p role="alert" className="error">
          Could not load your current Delivery. Reload the page.
        </p>
      )}
      {current.isSuccess && delivery === null && (
        <p className="courier-empty">No Delivery is currently assigned to you.</p>
      )}
      {delivery !== null && (
        <>
          <p className="courier-delivery-ref">{delivery.reference}</p>
          <dl className="address-pair">
            <div className="address-point">
              <dt>Pickup</dt>
              <dd>{delivery.pickupAddressLabel}</dd>
            </div>
            <div className="address-point">
              <dt>Handoff</dt>
              <dd>{delivery.handoffAddressLabel}</dd>
            </div>
          </dl>
          <button
            type="button"
            className="btn-primary courier-confirm"
            onClick={() => progress(delivery, step.action)}
            disabled={step.mutation.isPending}
            aria-busy={step.mutation.isPending}
          >
            {step.label}
          </button>
          {step.mutation.isError && (
            <p role="alert" className="error">
              {progressMessageFor(step.mutation.error)}
            </p>
          )}
        </>
      )}
    </section>
  )
}

function progressMessageFor(error: unknown): string {
  if (error instanceof ApiError && error.code === 'delivery-version-conflict') {
    return 'This Delivery changed. The page is refreshing its current state.'
  }
  if (error instanceof ApiError && error.code === 'delivery-invalid-transition') {
    return 'That confirmation is no longer valid for this Delivery.'
  }
  return 'Could not save the confirmation. Try again.'
}

/** Two initials for the identity avatar, drawn from the Courier's display name. */
function initialsOf(displayName: string): string {
  const words = displayName.trim().split(/\s+/).filter(Boolean)
  if (words.length === 0) {
    return '?'
  }
  const first = words[0][0]
  const last = words.length > 1 ? words[words.length - 1][0] : ''
  return (first + last).toUpperCase()
}

/** Maps a sharing state to the dot colour that opens its card. */
function sharingDotClass(status: SharingStatus): string {
  switch (status) {
    case 'REPORTING':
      return 'is-on'
    case 'OFF':
      return 'is-off'
    default:
      return 'is-warn'
  }
}

/**
 * The countdown has to keep moving between requests, because the position is deleted by the passing
 * of time rather than by anything either side does.
 */
function useTicker(): number {
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    const tick = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(tick)
  }, [])

  return now
}
