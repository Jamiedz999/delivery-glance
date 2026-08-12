import type { FullConfig } from '@playwright/test'
import { MAP_STYLE_URL, startStyleServer } from './mapStyle'

/**
 * Fails before the first journey rather than during it, because "connection refused" arriving as a
 * navigation timeout inside a three-role walkthrough reads like a product bug for as long as it
 * takes someone to scroll back up.
 *
 * It checks two things and starts one: that the application answers, that it was told to load the
 * style the journeys serve, and then the little server that serves it.
 */
export default async function globalSetup(config: FullConfig) {
  const baseURL = config.projects[0]?.use.baseURL ?? 'http://localhost:8080'

  await expectHealthy(baseURL)
  await expectConfiguredMapStyle(baseURL)

  const server = await startStyleServer(MAP_STYLE_URL)
  return async () => {
    await new Promise<void>((resolve) => server.close(() => resolve()))
  }
}

async function expectHealthy(baseURL: string): Promise<void> {
  const health = new URL('/actuator/health', baseURL)

  let response: Response
  try {
    response = await fetch(health)
  } catch (cause) {
    throw new Error(`${START_THE_TARGET}\nNothing is answering at ${baseURL}.`, { cause })
  }
  if (!response.ok) {
    throw new Error(`${health.href} answered ${response.status}. The application is up but not healthy.`)
  }
}

/**
 * Reads the style the running deployment was configured with, straight off the page that carries
 * it. Anything else would be trusting the shell that started the container to have agreed with the
 * shell that started the tests.
 */
async function expectConfiguredMapStyle(baseURL: string): Promise<void> {
  const page = await (await fetch(new URL('/track', baseURL))).text()
  const configured = /<meta name="delivery-glance-map-style" content="([^"]*)">/.exec(page)?.[1] ?? ''

  if (configured !== MAP_STYLE_URL) {
    throw new Error(
      `${START_THE_TARGET}\nIt is currently configured with ` +
        `${configured === '' ? 'no map style' : configured} instead.`,
    )
  }
}

const START_THE_TARGET =
  'These journeys run against the production-like image, configured with the map style they serve:\n' +
  `  TRACKING_MAP_STYLE_URL=${MAP_STYLE_URL} docker compose up --build --wait`
