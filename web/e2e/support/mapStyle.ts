import { createServer, type Server } from 'node:http'

/**
 * A map style for the journeys to point at, served from the machine running them.
 *
 * The map is a release input: `TRACKING_MAP_STYLE_URL` is unset in the default Compose demo, and
 * the Recipient page is expected to keep every word of its status and freshness content and say the
 * map is unavailable. That deployment is covered by the component tests, which is the right place
 * for it — they can assert the fallback without a browser at all.
 *
 * What only a browser can show is the other deployment: MapLibre actually mounting inside the
 * bundled application's Content-Security-Policy, and the Courier's marker actually leaving the map
 * when their reading expires. So the E2E target is started with a style configured, and this is it.
 *
 * The style is deliberately empty of sources. A tile provider is somebody's paid account and has no
 * place in a test; a background layer is enough for MapLibre to report `load`, and markers are DOM
 * elements drawn over the canvas rather than anything the tiles produce.
 */
const STYLE = {
  version: 8,
  name: 'Delivery Glance journeys',
  sources: {},
  layers: [{ id: 'background', type: 'background', paint: { 'background-color': '#e8edf1' } }],
}

/** Where the journeys expect the style to be, and what Compose must be told to load. */
export const MAP_STYLE_URL = 'http://127.0.0.1:9099/style.json'

export function startStyleServer(styleUrl: string): Promise<Server> {
  const url = new URL(styleUrl)
  const server = createServer((request, response) => {
    if (request.url !== url.pathname) {
      response.writeHead(404).end()
      return
    }
    response.writeHead(200, {
      'Content-Type': 'application/json',
      // MapLibre fetches the style cross-origin, which the application's policy allows and the
      // browser will still refuse without this.
      'Access-Control-Allow-Origin': '*',
    })
    response.end(JSON.stringify(STYLE))
  })

  return new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(Number(url.port), url.hostname, () => resolve(server))
  })
}
