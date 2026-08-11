import { useEffect, useRef, useState } from 'react'
import { MAP_UNAVAILABLE } from './copy'
import { type MapEngine, type MapMarker, type MountedMap, maplibreMapEngine } from './mapEngine'

interface DeliveryMapProps {
  /** Runtime configuration. Empty is a supported deployment, not an error. */
  styleUrl: string
  markers: MapMarker[]
  label: string
  engine?: MapEngine
}

/**
 * The one component that draws a map, and the only place in the application that knows a map
 * exists.
 *
 * It is never rendered outside In Transit, so nothing here has to decide whether a Courier may be
 * shown. What it does decide is what happens when the map cannot be drawn — no configured style, or
 * a style that fails to load — and the answer is always the honest one: say so, and leave the
 * status and freshness text the page already carries standing.
 */
export function DeliveryMap({ styleUrl, markers, label, engine = maplibreMapEngine }: DeliveryMapProps) {
  const container = useRef<HTMLDivElement>(null)
  const mounted = useRef<MountedMap | null>(null)
  const latestMarkers = useRef(markers)
  const [unavailable, setUnavailable] = useState(styleUrl === '')

  useEffect(() => {
    if (styleUrl === '' || container.current === null) {
      return
    }
    let abandoned = false
    // Each mount gets its own element inside the box rather than the box itself. React runs this
    // effect twice in development, so a second map starts before the first has finished loading,
    // and a renderer tearing itself down empties the element it was given — which, shared, is the
    // element the surviving map is drawing into. Two hosts means teardown can only reach its own.
    const surface = document.createElement('div')
    surface.className = 'map-surface'
    container.current.appendChild(surface)

    engine
      .mount(surface, { styleUrl, markers })
      .then((map) => {
        if (abandoned) {
          map.destroy()
          return
        }
        mounted.current = map
        // A reading can expire while the style is still downloading, and the markers handed to
        // mount above would then already be out of date. This is the catch-up.
        map.setMarkers(latestMarkers.current)
        setUnavailable(false)
      })
      .catch(() => {
        if (!abandoned) {
          setUnavailable(true)
        }
      })

    return () => {
      abandoned = true
      mounted.current?.destroy()
      mounted.current = null
      surface.remove()
    }
    // Deliberately not keyed on markers: a new report updates the existing map through the effect
    // below rather than tearing the map down and building another one, which would reload tiles and
    // lose wherever the reader had panned to.
    // oxlint-disable-next-line react-hooks/exhaustive-deps
  }, [engine, styleUrl])

  useEffect(() => {
    latestMarkers.current = markers
    mounted.current?.setMarkers(markers)
  }, [markers])

  if (unavailable) {
    return (
      <p className="map-unavailable" role="status">
        {MAP_UNAVAILABLE}
      </p>
    )
  }
  return <div className="map" role="img" aria-label={label} ref={container} />
}
