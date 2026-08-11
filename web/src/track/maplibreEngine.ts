import type { FeatureCollection } from 'geojson'
import { type GeoJSONSource, LngLatBounds, Map as MaplibreMap, Marker, setWorkerUrl } from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
// MapLibre parses tiles in a worker it loads from a file it expects to find beside its own module.
// Bundled, that file is nowhere: the URL is built at runtime from `import.meta.url`, so no bundler
// can see it, and the request 404s. The failure is silent in the worst way — the map reports neither
// `load` nor `error` and renders an empty box forever. `?worker&url` makes Vite build that file as
// its own bundle, resolve the shared module it imports, and hand back the URL it was written to.
import maplibreWorkerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url'
import { accuracyRing, type MapMarker, type MapOptions, type MountedMap } from './mapEngine'

setWorkerUrl(maplibreWorkerUrl)

/**
 * The only module in the application that imports MapLibre, and it is only ever reached through a
 * dynamic import from `mapEngine`. A Recipient whose Delivery is Awaiting, Assigned, Delivered or
 * Cancelled therefore downloads none of it, and neither does one whose link was refused.
 *
 * It is untested by design rather than by omission: everything here is a call into a WebGL renderer
 * jsdom cannot provide, and the two facts worth asserting about it — the style URL it is given and
 * the coordinates it is asked to draw — are asserted at the seam instead.
 */

const ACCURACY_SOURCE = 'courier-accuracy'

const ACCURACY_FILL = 'courier-accuracy-fill'

const ACCURACY_OUTLINE = 'courier-accuracy-outline'

const MARKER_COLOURS: Record<MapMarker['kind'], string> = {
  handoff: '#1f6f43',
  courier: '#1b4fd8',
}

const MARKER_LABELS: Record<MapMarker['kind'], string> = {
  handoff: 'Handoff address',
  courier: 'Courier’s last reported position',
}

export function mountMaplibre(container: HTMLElement, options: MapOptions): Promise<MountedMap> {
  const map = new MaplibreMap({
    container,
    style: options.styleUrl,
    // Framed on the markers at load and never moved afterwards. Easing the camera to each new
    // report would draw a journey between two points nobody measured, which is the extrapolation
    // ADR 05 rules out — in the one place a reader would take it for observed movement.
    bounds: boundsOf(options.markers),
    fitBoundsOptions: { padding: 56, maxZoom: 16 },
    attributionControl: { compact: true },
  })

  let markers: Marker[] = []

  function draw(next: MapMarker[]) {
    for (const marker of markers) {
      marker.remove()
    }
    markers = next.map((position) =>
      new Marker({ color: MARKER_COLOURS[position.kind] })
        .setLngLat([position.longitude, position.latitude])
        .addTo(map),
    )
    for (const [index, position] of next.entries()) {
      markers[index].getElement().setAttribute('aria-label', MARKER_LABELS[position.kind])
    }
    const source = map.getSource(ACCURACY_SOURCE) as GeoJSONSource | undefined
    // Fire and forget: the promise resolves when the tile worker has re-parsed the ring, and there
    // is nothing useful to do with that. A failure leaves the previous circle on screen, which is
    // wrong by at most one reading and is corrected by the next one.
    void source?.setData(accuracyFeatureFor(next))
  }

  return new Promise<MountedMap>((resolve, reject) => {
    let settled = false

    map.once('load', () => {
      map.addSource(ACCURACY_SOURCE, { type: 'geojson', data: accuracyFeatureFor(options.markers) })
      map.addLayer({
        id: ACCURACY_FILL,
        type: 'fill',
        source: ACCURACY_SOURCE,
        paint: { 'fill-color': MARKER_COLOURS.courier, 'fill-opacity': 0.12 },
      })
      map.addLayer({
        id: ACCURACY_OUTLINE,
        type: 'line',
        source: ACCURACY_SOURCE,
        paint: { 'line-color': MARKER_COLOURS.courier, 'line-opacity': 0.4, 'line-width': 1 },
      })
      draw(options.markers)
      settled = true
      resolve({
        setMarkers: draw,
        destroy: () => {
          draw([])
          map.remove()
        },
      })
    })

    // A style that cannot be fetched or parsed. The page has to hear about it so it can say the map
    // is unavailable rather than leaving a blank rectangle where a map was promised.
    map.once('error', (event) => {
      if (!settled) {
        settled = true
        map.remove()
        reject(event.error ?? new Error('The map style could not be loaded'))
      }
    })
  })
}

function boundsOf(markers: MapMarker[]): LngLatBounds {
  const bounds = new LngLatBounds()
  for (const marker of markers) {
    bounds.extend([marker.longitude, marker.latitude])
  }
  return bounds
}

/** One polygon per marker that carries an accuracy, which in Core is only ever the courier. */
function accuracyFeatureFor(markers: MapMarker[]): FeatureCollection {
  return {
    type: 'FeatureCollection',
    features: markers
      .filter((marker) => marker.accuracyMetres !== undefined && marker.accuracyMetres > 0)
      .map((marker) => ({
        type: 'Feature',
        properties: {},
        geometry: { type: 'Polygon', coordinates: [accuracyRing(marker)] },
      })),
  }
}
