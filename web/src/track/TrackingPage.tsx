import { useCallback, useEffect, useMemo, useState } from 'react'
import { describeFreshness } from '../freshness'
import { DeliveryMap } from './DeliveryMap'
import { STATE_COPY, UNAVAILABLE_LINK, UNREACHABLE, formatAge, formatTime } from './copy'
import type { MapEngine, MapMarker } from './mapEngine'
import { type TrackingResult, type TrackingSnapshot, fetchSnapshot } from './tracking'

interface TrackingPageProps {
  /** The configured map style, empty when this deployment has none. */
  mapStyleUrl: string
  /** Tests supply a recording engine; production gets MapLibre, loaded on first use. */
  mapEngine?: MapEngine
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
export function TrackingPage({ mapStyleUrl, mapEngine }: TrackingPageProps) {
  const [result, setResult] = useState<TrackingResult | null>(null)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let abandoned = false
    setResult(null)
    void fetchSnapshot().then((loaded) => {
      if (!abandoned) {
        setResult(loaded)
      }
    })
    return () => {
      abandoned = true
    }
  }, [attempt])

  const retry = useCallback(() => setAttempt((previous) => previous + 1), [])

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
      <p className="notice" role="alert">
        {UNAVAILABLE_LINK}
      </p>
    )
  }
  if (result.status === 'unreachable') {
    return (
      <div className="notice">
        <p role="alert">{UNREACHABLE}</p>
        <button type="button" onClick={retry}>
          Try again
        </button>
      </div>
    )
  }
  return <Delivery snapshot={result.snapshot} mapStyleUrl={mapStyleUrl} mapEngine={mapEngine} />
}

function Delivery({ snapshot, mapStyleUrl, mapEngine }: { snapshot: TrackingSnapshot } & TrackingPageProps) {
  const copy = STATE_COPY[snapshot.state]

  return (
    <section className="delivery" aria-labelledby="tracking-headline">
      <h2 id="tracking-headline">{copy.headline}</h2>
      <p className="next-step">{copy.nextStep}</p>

      {snapshot.reference !== null && (
        <p className="reference">
          Delivery <strong>{snapshot.reference}</strong>
        </p>
      )}

      {snapshot.handoffAddressLabel !== null && (
        <section aria-labelledby="handoff-heading">
          <h3 id="handoff-heading">Handoff address</h3>
          <p>{snapshot.handoffAddressLabel}</p>
        </section>
      )}

      {snapshot.courierDisplayName !== null && (
        <section aria-labelledby="courier-heading">
          <h3 id="courier-heading">Courier</h3>
          <p>{snapshot.courierDisplayName}</p>
        </section>
      )}

      {snapshot.map !== null && (
        <CourierLocation map={snapshot.map} mapStyleUrl={mapStyleUrl} mapEngine={mapEngine} />
      )}

      {snapshot.completedAt !== null && (
        <p className="completed">
          {snapshot.state === 'DELIVERED' ? 'Handed over at ' : 'Cancelled at '}
          <time dateTime={snapshot.completedAt}>{formatTime(snapshot.completedAt)}</time>
        </p>
      )}

      {snapshot.state === 'CANCELLED' && (
        <p className="contact">
          {snapshot.deliveryTeamContact === null
            ? 'For questions, contact the delivery team through the channel that shared this link.'
            : `For questions, contact the delivery team on ${snapshot.deliveryTeamContact}.`}
        </p>
      )}
    </section>
  )
}

/**
 * The map and the sentence that qualifies it, which are one section because the sentence is what
 * makes the map honest. If the map cannot be drawn at all, the sentence is still here and still
 * says the same thing.
 */
function CourierLocation({
  map,
  mapStyleUrl,
  mapEngine,
}: { map: NonNullable<TrackingSnapshot['map']> } & TrackingPageProps) {
  const courier = map.courier
  const freshness = useFreshness(courier?.recordedAt ?? null)
  // The browser's own timer is what removes the marker. Nothing arrives from the server to say the
  // reading expired, so a page left open on a phone in a pocket has to reach this on its own.
  const stillUsable = freshness !== null && freshness.label !== 'Unavailable'

  const markers = useMemo<MapMarker[]>(() => {
    const drawn: MapMarker[] = [{ kind: 'handoff', ...map.handoff }]
    if (courier !== null && stillUsable) {
      drawn.push({
        kind: 'courier',
        latitude: courier.latitude,
        longitude: courier.longitude,
        accuracyMetres: courier.accuracyMetres,
      })
    }
    return drawn
  }, [map.handoff, courier, stillUsable])

  return (
    <section aria-labelledby="location-heading">
      <h3 id="location-heading">Courier location</h3>
      <p className="freshness" role="status">
        {describeLocation(courier, freshness, stillUsable)}
      </p>
      <DeliveryMap
        styleUrl={mapStyleUrl}
        markers={markers}
        engine={mapEngine}
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
  freshness: ReturnType<typeof describeFreshness>,
  stillUsable: boolean,
): string {
  if (courier === null || freshness === null) {
    return 'The courier is not sharing their position right now.'
  }
  if (!stillUsable) {
    return `Location unavailable — last reported ${formatAge(freshness.ageSeconds)}.`
  }
  const accuracy = `accurate to about ${Math.round(courier.accuracyMetres)} metres`
  return freshness.label === 'Live'
    ? `Live location — updated ${formatAge(freshness.ageSeconds)}, ${accuracy}.`
    : `Delayed location — updated ${formatAge(freshness.ageSeconds)}, ${accuracy}.`
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
