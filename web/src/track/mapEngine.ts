/**
 * The seam the map lives behind.
 *
 * Production renders with MapLibre, which needs WebGL that jsdom does not have, so a component test
 * that mounted the real thing would either fail or be testing a stub of MapLibre's own making.
 * Tests supply a recording engine instead and assert on exactly the two things that matter about
 * the boundary: the style URL this page hands out, and the coordinates it asks to be drawn.
 *
 * The MapLibre implementation is imported only when a map is actually mounted, so a Recipient whose
 * Delivery is not In Transit never downloads it.
 */

export interface MapMarker {
  kind: 'handoff' | 'courier'
  latitude: number
  longitude: number
  /**
   * The reading's own accuracy, drawn as a circle around the courier. Absent for the handoff, which
   * is an address rather than a measurement and has no radius to be honest about.
   */
  accuracyMetres?: number
}

export interface MapOptions {
  styleUrl: string
  markers: MapMarker[]
}

export interface MountedMap {
  setMarkers(markers: MapMarker[]): void
  destroy(): void
}

export interface MapEngine {
  mount(container: HTMLElement, options: MapOptions): Promise<MountedMap>
}

export const maplibreMapEngine: MapEngine = {
  async mount(container, options) {
    const { mountMaplibre } = await import('./maplibreEngine')
    return mountMaplibre(container, options)
  },
}

const EARTH_RADIUS_METRES = 6_371_008.8

const CIRCLE_POINTS = 48

/**
 * The accuracy radius as a closed ring of longitude/latitude pairs.
 *
 * MapLibre sizes a circle layer in screen pixels, which would make the radius shrink as the reader
 * zooms out and quietly turn a hundred-metre uncertainty into a confident dot. A polygon in real
 * coordinates keeps saying the same thing at every zoom, which is the whole point of drawing it.
 *
 * It lives here rather than inside the MapLibre module because it is arithmetic, and arithmetic is
 * the part of a map that can be tested without a graphics context.
 */
export function accuracyRing(centre: MapMarker, points = CIRCLE_POINTS): [number, number][] {
  const radiusMetres = centre.accuracyMetres ?? 0
  const latitudeDegrees = (radiusMetres / EARTH_RADIUS_METRES) * (180 / Math.PI)
  const longitudeDegrees = latitudeDegrees / Math.cos((centre.latitude * Math.PI) / 180)

  const ring: [number, number][] = []
  for (let point = 0; point <= points; point++) {
    const angle = (point / points) * 2 * Math.PI
    ring.push([
      centre.longitude + longitudeDegrees * Math.cos(angle),
      centre.latitude + latitudeDegrees * Math.sin(angle),
    ])
  }
  return ring
}
