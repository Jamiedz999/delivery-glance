import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { type FreshnessDescription, type FreshnessLabel, describeFreshness } from '../freshness'
import { DeliveryMap } from './DeliveryMap'
import {
  TRACKING_CONNECTION_COPY,
  NO_POSITION,
  STATE_COPY,
  UNAVAILABLE_LINK,
  UNREACHABLE,
  formatAge,
  formatTime,
} from './copy'
import type { MapEngine, MapMarker } from './mapEngine'
import {
  type RecipientState,
  type TrackingMap,
  type TrackingResult,
  type TrackingSnapshot,
  fetchSnapshot,
} from './tracking'
import { type OpenUpdates, type TrackingConnection, openUpdates, useSnapshotUpdates } from './updates'

/** Everything about the map that comes from deployment rather than from the Delivery. */
export interface MapConfiguration {
  /** The configured map style, empty when this deployment has none. */
  styleUrl: string
  /** Tests supply a recording engine; production gets MapLibre, loaded on first use. */
  engine?: MapEngine
}

interface TrackingPageProps {
  map: MapConfiguration
  /** Tests drive the stream by hand; production gets the real EventSource. */
  updates?: OpenUpdates
}

/**
 * Everything a Link Holder sees after their link has been accepted.
 *
 * It renders what the snapshot carries and nothing else. There is no field it hides and no state it
 * infers: the server already decided what this state may say, so a missing Courier here is a
 * Courier the Recipient is not being told about rather than one this page chose to leave out.
 *
 * The one thing it does decide for itself is age. The server reports when a position was measured;
 * how old that is now is a question only this page can keep answering, because nothing arrives to
 * tell it the marker has expired.
 */
export function TrackingPage({ map, updates = openUpdates }: TrackingPageProps) {
  const [result, setResult] = useState<TrackingResult | null>(null)
  const mounted = useRef(true)

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  /**
   * @param keepWhatIsShown whether a browser that could not ask should be allowed to replace the
   * delivery on screen with an error. It must not when the read was this page's own idea: a
   * reconnect fires exactly when the network has been unreliable, and turning a moment of that into
   * "could not reach the delivery service" would throw away a perfectly good snapshot and its
   * timestamps. A read the reader asked for is the opposite — silence there looks broken.
   */
  const load = useCallback(async (keepWhatIsShown: boolean) => {
    const loaded = await fetchSnapshot()
    if (!mounted.current || (keepWhatIsShown && loaded.status === 'unreachable')) {
      return
    }
    setResult(loaded)
  }, [])

  useEffect(() => {
    void load(false)
  }, [load])

  const refresh = useCallback(() => void load(true), [load])
  const connection = useSnapshotUpdates(refresh, updates)

  const retry = useCallback(() => {
    setResult(null)
    void load(false)
  }, [load])

  if (result === null) {
    return (
      <p className="notice" role="status">
        Loading your delivery…
      </p>
    )
  }
  if (result.status === 'unavailable') {
    // No retry offered. The server's refusal is final by design, and a button that re-asked would
    // suggest the link might come back.
    return (
      <p className="notice notice-alert" role="alert">
        {UNAVAILABLE_LINK}
      </p>
    )
  }
  if (result.status === 'unreachable') {
    return (
      <div className="notice notice-alert">
        <p role="alert">{UNREACHABLE}</p>
        <button type="button" onClick={retry}>
          Try again
        </button>
      </div>
    )
  }
  return <Delivery snapshot={result.snapshot} map={map} connection={connection} />
}

/** The ordered lifecycle a Recipient is shown, and the single word each milestone is named by. */
const PROGRESS_STEPS: readonly { state: RecipientState; label: string }[] = [
  { state: 'AWAITING_COURIER', label: 'Preparing' },
  { state: 'ASSIGNED', label: 'Courier assigned' },
  { state: 'IN_TRANSIT', label: 'On the way' },
  { state: 'DELIVERED', label: 'Delivered' },
]

/**
 * The states that are still moving, and the only ones that show a progress track. A terminal
 * Delivery is reduced to its outcome instead: a finished track is a shape that invites the reader
 * to wait for a next step that is not coming.
 */
function isInProgress(state: RecipientState): boolean {
  return state === 'AWAITING_COURIER' || state === 'ASSIGNED' || state === 'IN_TRANSIT'
}

function Delivery({
  snapshot,
  map,
  connection,
}: {
  snapshot: TrackingSnapshot
  map: MapConfiguration
  connection: TrackingConnection
}) {
  const copy = STATE_COPY[snapshot.state]

  return (
    <section className="delivery" aria-labelledby="tracking-headline">
      <header className={`status-banner ${bannerToneClass(snapshot.state)}`}>
        <span className="status-banner-dot" aria-hidden="true" />
        <div className="status-banner-text">
          <h2 id="tracking-headline">{copy.headline}</h2>
          <p className="next-step">{copy.nextStep}</p>
        </div>
      </header>

      {isInProgress(snapshot.state) && <Progress state={snapshot.state} />}

      {snapshot.map !== null && <CourierLocation positions={snapshot.map} map={map} />}

      {(snapshot.reference !== null ||
        snapshot.handoffAddressLabel !== null ||
        snapshot.courierDisplayName !== null ||
        snapshot.completedAt !== null) && (
        <div className="card delivery-facts">
          {snapshot.reference !== null && (
            <p className="reference">
              Delivery <strong>{snapshot.reference}</strong>
            </p>
          )}

          {snapshot.handoffAddressLabel !== null && (
            <section className="fact" aria-labelledby="handoff-heading">
              <h3 id="handoff-heading">Handoff address</h3>
              <p>{snapshot.handoffAddressLabel}</p>
            </section>
          )}

          {snapshot.courierDisplayName !== null && (
            <section className="fact" aria-labelledby="courier-heading">
              <h3 id="courier-heading">Courier</h3>
              <p>{snapshot.courierDisplayName}</p>
            </section>
          )}

          {snapshot.completedAt !== null && (
            <p className="completed">
              {snapshot.state === 'DELIVERED' ? 'Handed over at ' : 'Cancelled at '}
              <time dateTime={snapshot.completedAt}>{formatTime(snapshot.completedAt)}</time>
            </p>
          )}
        </div>
      )}

      {snapshot.state === 'CANCELLED' && (
        <p className="contact">
          {snapshot.deliveryTeamContact === null
            ? 'For questions, contact the delivery team through the channel that shared this link.'
            : `For questions, contact the delivery team on ${snapshot.deliveryTeamContact}.`}
        </p>
      )}

      {/*
        Deliberately not a live region, for the same reason the freshness sentence above is not one:
        a phone moving between cells can flap between connected and reconnecting several times a
        minute, and announcing each one would talk over everything else on the page. It is on screen
        for a reader who wonders why nothing is changing, and silent for one who does not.
      */}
      <p className="connection">{TRACKING_CONNECTION_COPY[connection]}</p>
    </section>
  )
}

/**
 * The lifecycle rendered as a stepper: what is done, where the Delivery is now, and what is still
 * ahead. It carries no times — the snapshot holds only the current state, so this shows step
 * completion and never a past timestamp it does not have.
 */
function Progress({ state }: { state: RecipientState }) {
  const currentIndex = PROGRESS_STEPS.findIndex((step) => step.state === state)

  return (
    <ol className="progress" aria-label="Delivery progress">
      {PROGRESS_STEPS.map((step, index) => {
        const status =
          index < currentIndex ? 'is-done' : index === currentIndex ? 'is-current' : 'is-upcoming'
        return (
          <li key={step.state} className={status} aria-current={index === currentIndex ? 'step' : undefined}>
            <span className="progress-dot" aria-hidden="true" />
            <span className="progress-label">{step.label}</span>
          </li>
        )
      })}
    </ol>
  )
}

/**
 * The map and the sentence that qualifies it, which are one section because the sentence is what
 * makes the map honest. If the map cannot be drawn at all, the sentence is still here and still
 * says the same thing.
 */
function CourierLocation({ positions, map }: { positions: TrackingMap; map: MapConfiguration }) {
  const courier = positions.courier
  const freshness = useFreshness(courier?.recordedAt ?? null)
  // The browser's own timer is what removes the marker. Nothing arrives from the server to say the
  // reading expired, so a page left open on a phone in a pocket has to reach this on its own.
  const stillUsable = freshness !== null && freshness.label !== 'Unavailable'

  const markers = useMemo<MapMarker[]>(() => {
    const drawn: MapMarker[] = [{ kind: 'handoff', ...positions.handoff }]
    if (courier !== null && stillUsable) {
      drawn.push({
        kind: 'courier',
        latitude: courier.latitude,
        longitude: courier.longitude,
        accuracyMetres: courier.accuracyMetres,
      })
    }
    return drawn
  }, [positions.handoff, courier, stillUsable])

  // A reading the browser has aged out, and one the server never held, both read as Unavailable —
  // the chip and the sentence below must agree, so the label is decided once here.
  const label: FreshnessLabel = freshness?.label ?? 'Unavailable'

  return (
    <section className="card location-card" aria-labelledby="location-heading">
      <div className="card-head">
        <h3 id="location-heading">Courier location</h3>
        {/* The chip repeats the freshness label at a glance; the sentence below carries it in prose. */}
        <span className={`freshness-chip ${freshnessChipClass(label)}`}>{label}</span>
      </div>
      {/*
        The sentence ticks every second, so it is deliberately not the live region: announcing
        "32 seconds ago", then "33", would talk over everything else on the page for as long as it
        is open. What is worth interrupting for is the reading changing category, which happens
        twice, and that is what the region below carries.
      */}
      <p className="freshness">{describeLocation(courier, freshness)}</p>
      <span className="visually-hidden" role="status">
        {freshness === null ? NO_POSITION : `${freshness.label} location.`}
      </span>
      <DeliveryMap
        styleUrl={map.styleUrl}
        markers={markers}
        engine={map.engine}
        label={
          stillUsable
            ? 'Map of the handoff address and the courier’s last reported position'
            : 'Map of the handoff address'
        }
      />
    </section>
  )
}

function describeLocation(
  courier: { accuracyMetres: number } | null,
  freshness: FreshnessDescription | null,
): string {
  if (courier === null || freshness === null) {
    return NO_POSITION
  }
  if (freshness.label === 'Unavailable') {
    // The last report time survives the marker, which is the whole difference between this and the
    // sentence above: here the page knows when the courier was last heard from and says so.
    return `Location unavailable — last reported ${formatAge(freshness.ageSeconds)}.`
  }
  const accuracy = `accurate to about ${Math.round(courier.accuracyMetres)} metres`
  return `${freshness.label} location — updated ${formatAge(freshness.ageSeconds)}, ${accuracy}.`
}

function bannerToneClass(state: RecipientState): string {
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

function freshnessChipClass(label: FreshnessLabel): string {
  switch (label) {
    case 'Live':
      return 'is-live'
    case 'Delayed':
      return 'is-delayed'
    case 'Unavailable':
      return 'is-unavailable'
  }
}

/**
 * Ages one reading in the browser. It restarts whenever a reload brings a different measurement
 * time, and stops entirely when there is no position, so a page showing no Courier is not running
 * a timer for one.
 */
function useFreshness(recordedAt: string | null) {
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    if (recordedAt === null) {
      return
    }
    setNow(Date.now())
    const tick = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(tick)
  }, [recordedAt])

  return describeFreshness(recordedAt, now)
}
