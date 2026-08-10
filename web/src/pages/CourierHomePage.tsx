import { useEffect, useState } from 'react'
import { describeFreshness, formatCountdown } from '../api/courier'
import { useCourier, useSetDuty } from '../api/queries'
import { SHARING_STATUS_LABELS, useLocationSharing } from './useLocationSharing'

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
    <section>
      <div className="page-heading">
        <h1>Courier workspace</h1>
      </div>

      <section aria-labelledby="duty-heading">
        <h2 id="duty-heading">Duty</h2>
        <p role="status">{courier.onDuty ? 'On duty' : 'Off duty'}</p>
        {courier.onDutyChangedAt !== null && (
          <p>
            Since <time dateTime={courier.onDutyChangedAt}>{new Date(courier.onDutyChangedAt).toLocaleString()}</time>
          </p>
        )}
        <button
          type="button"
          onClick={() => duty.mutate(!courier.onDuty)}
          disabled={duty.isPending}
          aria-busy={duty.isPending}
        >
          {courier.onDuty ? 'Go off duty' : 'Go on duty'}
        </button>
        {duty.isError && <p role="alert">Your duty change was not saved. Try again.</p>}
        <p>Going on duty does not share your location. That is a separate decision.</p>
      </section>

      <section aria-labelledby="sharing-heading">
        <h2 id="sharing-heading">Location sharing</h2>
        <p role="status">{SHARING_STATUS_LABELS[sharing.status]}</p>
        {sharing.notice !== null && <p role="alert">{sharing.notice}</p>}

        <p>
          Your position is only reported while this page is open and in front of you. Reloading,
          signing out or pressing Stop ends it, and the server forgets where you are.
        </p>

        {leftoverSession && (
          <p>
            An earlier page started sharing at{' '}
            <time dateTime={courier.sharing?.startedAt}>
              {new Date(courier.sharing?.startedAt ?? '').toLocaleTimeString()}
            </time>
            . This page holds no reporting secret for it, so it can only replace that session or end
            it.
          </p>
        )}

        {sharing.status === 'OFF' && (
          <button type="button" onClick={() => void sharing.start()} disabled={sharing.busy} aria-busy={sharing.busy}>
            Start sharing
          </button>
        )}
        {/* Offered for a session this page did not start too, so a reload cannot strand one. */}
        {(sharing.status !== 'OFF' || leftoverSession) && (
          <button type="button" onClick={() => void sharing.stop()} disabled={sharing.busy} aria-busy={sharing.busy}>
            Stop sharing
          </button>
        )}

        <h3>Position held by the server</h3>
        {freshness === null || freshness.label === 'Unavailable' ? (
          <p>Unavailable — the server holds no usable position for you.</p>
        ) : (
          <p>
            {freshness.label} — measured {freshness.ageSeconds} seconds ago, removed in{' '}
            {formatCountdown(freshness.secondsUntilUnavailable)}
            {courier.location.accuracyMetres !== null &&
              `, accurate to about ${Math.round(courier.location.accuracyMetres)} metres`}
          </p>
        )}
      </section>
    </section>
  )
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
