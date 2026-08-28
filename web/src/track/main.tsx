import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { TrackingPage } from './TrackingPage'

/**
 * The Recipient application's entry point.
 *
 * Nothing runs it on page load. The /track bootstrap requests this bundle by a fixed path only
 * after the token exchange has succeeded, which is what makes "the map is never loaded before a
 * successful bootstrap" a fact about what the browser fetched rather than a rule this code has to
 * remember.
 *
 * The map style arrives in a meta tag rather than through the snapshot, because it is deployment
 * configuration that is identical for every visitor and has nothing to do with anybody's Delivery.
 * Passing it as a prop, read once here, keeps every component below testable without a document.
 */
const container = document.getElementById('tracking-app')

if (container !== null) {
  // The placeholder the bootstrap has been writing status into. The application owns the page from
  // here, so it goes rather than sitting above it holding a message that is no longer true.
  document.getElementById('tracking-status')?.remove()

  createRoot(container).render(
    <StrictMode>
      <TrackingPage map={{ styleUrl: mapStyleUrl() }} etaEnabled={etaEnabled()} />
    </StrictMode>,
  )
}

function mapStyleUrl(): string {
  return document.querySelector<HTMLMetaElement>('meta[name="delivery-glance-map-style"]')?.content ?? ''
}

/**
 * Whether this deployment computes ETAs, read from the same kind of meta tag as the map style — a
 * deployment fact identical for every visitor. Absent or anything but "true" is off, so a page
 * served without the tag simply shows no arrival section.
 */
function etaEnabled(): boolean {
  return (
    document.querySelector<HTMLMetaElement>('meta[name="delivery-glance-eta-enabled"]')?.content === 'true'
  )
}
