import { StrictMode } from 'react'
import { act, cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { jsonResponse, problemResponse, urlOf } from '../testing/support'
import { TrackingPage } from './TrackingPage'
import type { MapEngine, MapMarker, MapOptions } from './mapEngine'
import type { RecipientState, TrackingSnapshot } from './tracking'
import type { OpenUpdates, UpdateHandlers } from './updates'

const STYLE_URL = 'https://tiles.delivery-glance.example/styles/core/style.json'

/** Fictional, and the thing a tile request must never carry. */
const GRANT_SECRET = 'grant-secret-the-tiles-must-never-see'

const DELIVERY_ID = '3f2b6c1e-9f0a-4d5b-8a11-6c2e7d4f9b30'

/** Only the tests that move the clock by hand need a fixed instant; the rest read the real one. */
const NOW = Date.parse('2026-08-10T09:00:00.000Z')

/**
 * A map engine that draws nothing and remembers everything it was asked to draw.
 *
 * MapLibre needs WebGL, which jsdom does not have, so this is not a shortcut around a testable
 * component — it is the only way to assert on the boundary at all. What it makes visible is exactly
 * the pair of facts the Issue names: the URL tiles would be fetched from, and the coordinates that
 * would be drawn.
 */
function recordingEngine() {
  const mounts: MapOptions[] = []
  const drawn: MapMarker[][] = []
  const surfaces: { element: HTMLElement; destroyed: boolean }[] = []

  const engine: MapEngine = {
    mount(container, options) {
      mounts.push(options)
      drawn.push(options.markers)
      const surface = { element: container, destroyed: false }
      surfaces.push(surface)
      return Promise.resolve({
        setMarkers: (markers) => {
          drawn.push(markers)
        },
        destroy: () => {
          surface.destroyed = true
          // What MapLibre's own remove() does: it empties the element it was handed.
          surface.element.replaceChildren()
        },
      })
    },
  }

  return {
    engine,
    mounts,
    surfaces,
    lastDrawn: () => drawn.at(-1) ?? [],
    kindsLastDrawn: () => (drawn.at(-1) ?? []).map((marker) => marker.kind),
  }
}

const failingEngine: MapEngine = {
  mount: () => Promise.reject(new Error('the style could not be loaded')),
}

function snapshotOf(state: RecipientState, overrides: Partial<TrackingSnapshot> = {}): TrackingSnapshot {
  return {
    reference: 'DG-0042',
    state,
    handoffAddressLabel: '2 Handoff Road, London',
    courierDisplayName: null,
    map: null,
    completedAt: null,
    deliveryTeamContact: null,
    proofOnFile: null,
    ...overrides,
  }
}

function inTransit(overrides: Partial<TrackingSnapshot> = {}): TrackingSnapshot {
  return snapshotOf('IN_TRANSIT', {
    courierDisplayName: 'Cory C.',
    map: {
      handoff: { latitude: 51.509, longitude: -0.13 },
      // Measured at whatever "now" is when the snapshot is built, so a test that has not touched
      // the clock still starts from a live reading.
      courier: {
        latitude: 51.5081,
        longitude: -0.129,
        accuracyMetres: 14,
        recordedAt: new Date(Date.now()).toISOString(),
      },
    },
    ...overrides,
  })
}

function respondWithSnapshot(snapshot: TrackingSnapshot) {
  vi.mocked(fetch).mockImplementation(() => Promise.resolve(jsonResponse(snapshot)))
}

function renderPage(engine?: MapEngine, styleUrl = STYLE_URL, updates?: OpenUpdates) {
  return render(<TrackingPage map={{ styleUrl, engine }} updates={updates} />)
}

/**
 * A refresh stream the test is holding the other end of.
 *
 * Nothing here fakes a network. What it stands in for is the browser's own EventSource, which jsdom
 * does not have — so the alternative is not a more realistic test, it is no test of this behaviour
 * at all. What it makes drivable is exactly the three things a real stream does to this page:
 * connect, hint, and go away either temporarily or for good.
 */
function fakeUpdates() {
  let handlers: UpdateHandlers | null = null
  let closes = 0

  const open: OpenUpdates = (given) => {
    handlers = given
    return () => {
      closes += 1
    }
  }

  const drive = async (use: (handlers: UpdateHandlers) => void) => {
    await act(async () => {
      if (handlers === null) {
        throw new Error('The page has not opened a stream.')
      }
      use(handlers)
    })
  }

  return {
    open,
    connect: () => drive((on) => on.onConnected()),
    hint: (version: number) => drive((on) => on.onChanged(version)),
    hintUnreadable: () => drive((on) => on.onChanged(Number.NaN)),
    drop: (retrying: boolean) => drive((on) => on.onDropped(retrying)),
    closeCount: () => closes,
  }
}

function snapshotRequests() {
  return vi
    .mocked(fetch)
    .mock.calls.map(([input]) => urlOf(input))
    .filter((url) => url === '/api/tracking/snapshot')
}

describe('the Recipient tracking view', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
    document.cookie = `DG_TRACKING=${GRANT_SECRET}`
  })

  afterEach(() => {
    // Explicit because this project does not run Vitest with globals, which is what Testing
    // Library's own automatic cleanup hooks itself onto.
    cleanup()
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('says it is loading before the first snapshot arrives, and shows no map yet', () => {
    const map = recordingEngine()
    vi.mocked(fetch).mockReturnValue(new Promise(() => {}))

    renderPage(map.engine)

    expect(screen.getByRole('status')).toHaveTextContent('Loading your delivery…')
    expect(map.mounts).toHaveLength(0)
  })

  it('gives an unavailable link one refusal and no way to ask again', async () => {
    vi.mocked(fetch).mockResolvedValue(problemResponse('tracking-link-unavailable', 404))

    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent('This tracking link is no longer available')
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('separates a browser that could not ask from a link that is gone, and offers a retry', async () => {
    vi.mocked(fetch).mockRejectedValueOnce(new TypeError('Failed to fetch'))
    renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not reach the delivery service')

    respondWithSnapshot(snapshotOf('AWAITING_COURIER'))
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('heading', { name: 'We’re preparing your delivery' })).toBeInTheDocument()
  })

  /** The one interactive control on the page has to be reachable and operable from a keyboard. */
  it('lets a keyboard reach and press the retry without a pointer', async () => {
    vi.mocked(fetch).mockRejectedValueOnce(new TypeError('Failed to fetch'))
    renderPage()
    await screen.findByRole('button', { name: 'Try again' })

    respondWithSnapshot(snapshotOf('AWAITING_COURIER'))
    await userEvent.tab()
    expect(screen.getByRole('button', { name: 'Try again' })).toHaveFocus()
    await userEvent.keyboard('{Enter}')

    expect(await screen.findByRole('heading', { name: 'We’re preparing your delivery' })).toBeInTheDocument()
  })

  it('shows a waiting Recipient the state, the next step and the handoff address only', async () => {
    const map = recordingEngine()
    respondWithSnapshot(snapshotOf('AWAITING_COURIER'))

    renderPage(map.engine)

    expect(await screen.findByRole('heading', { name: 'We’re preparing your delivery' })).toBeInTheDocument()
    expect(screen.getByText('A courier is being arranged to collect it.')).toBeInTheDocument()
    expect(screen.getByText('2 Handoff Road, London')).toBeInTheDocument()
    expect(screen.getByText('DG-0042')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Courier' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Courier location' })).not.toBeInTheDocument()
    expect(map.mounts).toHaveLength(0)
  })

  it('names the courier once assigned and still draws no map', async () => {
    const map = recordingEngine()
    respondWithSnapshot(snapshotOf('ASSIGNED', { courierDisplayName: 'Cory C.' }))

    renderPage(map.engine)

    expect(await screen.findByRole('heading', { name: 'A courier has been assigned' })).toBeInTheDocument()
    expect(screen.getByText('Cory C.')).toBeInTheDocument()
    expect(map.mounts).toHaveLength(0)
  })

  it('draws the handoff and the courier once In Transit, with the accuracy of the reading', async () => {
    const map = recordingEngine()
    respondWithSnapshot(inTransit())

    renderPage(map.engine)

    expect(await screen.findByRole('heading', { name: 'Your delivery is on the way' })).toBeInTheDocument()
    await waitFor(() => expect(map.mounts).toHaveLength(1))
    expect(map.lastDrawn()).toEqual([
      { kind: 'handoff', latitude: 51.509, longitude: -0.13 },
      { kind: 'courier', latitude: 51.5081, longitude: -0.129, accuracyMetres: 14 },
    ])
    expect(
      screen.getByText(/Live location — updated just now, accurate to about 14 metres/),
    ).toBeInTheDocument()
  })

  it('marks where the delivery is on a progress track that carries no times', async () => {
    respondWithSnapshot(inTransit())

    renderPage(recordingEngine().engine)
    await screen.findByRole('heading', { name: 'Your delivery is on the way' })

    const progress = screen.getByRole('list', { name: 'Delivery progress' })
    // The state on screen is the current step, and the ones before it are done.
    expect(within(progress).getByRole('listitem', { current: 'step' })).toHaveTextContent('On the way')
    // Step completion only: no clock time is shown against any milestone.
    expect(progress).not.toHaveTextContent(/\d{1,2}:\d{2}/)
  })

  /** A terminal delivery is reduced to its outcome; a finished track would invite a wait for nothing. */
  it('drops the progress track once the delivery has reached a final state', async () => {
    respondWithSnapshot(snapshotOf('DELIVERED', { completedAt: '2026-08-10T09:42:00.000Z' }))

    renderPage(recordingEngine().engine)
    await screen.findByRole('heading', { name: 'Delivered' })

    expect(screen.queryByRole('list', { name: 'Delivery progress' })).not.toBeInTheDocument()
  })

  it('tells the recipient proof is on file when a delivery was confirmed with it', async () => {
    respondWithSnapshot(
      snapshotOf('DELIVERED', { completedAt: '2026-08-10T09:42:00.000Z', proofOnFile: true }),
    )

    renderPage(recordingEngine().engine)
    await screen.findByRole('heading', { name: 'Delivered' })

    expect(screen.getByText('Confirmed with proof on file.')).toBeInTheDocument()
  })

  it('says nothing about proof when a delivery was confirmed without it', async () => {
    respondWithSnapshot(
      snapshotOf('DELIVERED', { completedAt: '2026-08-10T09:42:00.000Z', proofOnFile: false }),
    )

    renderPage(recordingEngine().engine)
    await screen.findByRole('heading', { name: 'Delivered' })

    expect(screen.queryByText('Confirmed with proof on file.')).not.toBeInTheDocument()
  })

  it('reads the freshness at a glance in a chip beside the location sentence', async () => {
    respondWithSnapshot(inTransit())

    renderPage(recordingEngine().engine)
    await screen.findByText(/Live location — updated/)

    expect(screen.getByText('Live', { selector: '.freshness-chip' })).toBeInTheDocument()
  })

  /**
   * The two boundaries, moved by the browser's own clock and nothing else. No second response
   * arrives in this test, which is the point: a page left open on a phone has to stop claiming to
   * know where the courier is without being told.
   */
  it('ages the reading through Live, Delayed and Unavailable with no further server response', async () => {
    const map = recordingEngine()
    // Not `shouldAdvanceTime`: real elapsed milliseconds would ride along with each advance and
    // land the assertions either side of the very boundaries this test exists to pin.
    vi.useFakeTimers()
    vi.setSystemTime(NOW)
    respondWithSnapshot(inTransit())

    renderPage(map.engine)
    await act(async () => {})
    expect(screen.getByText(/Live location — updated just now/)).toBeInTheDocument()

    await act(async () => {
      vi.advanceTimersByTime(30_000)
    })
    expect(screen.getByText(/Live location — updated 30 seconds ago/)).toBeInTheDocument()
    expect(map.kindsLastDrawn()).toEqual(['handoff', 'courier'])
    // Thirty seconds of ticking, and the live region has said one thing. The sentence is not the
    // region precisely so that a reader is not told the age every second for as long as they look.
    expect(screen.getByRole('status')).toHaveTextContent('Live location.')

    await act(async () => {
      vi.advanceTimersByTime(1_000)
    })
    expect(screen.getByText(/Delayed location — updated 31 seconds ago/)).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Delayed location.')

    await act(async () => {
      vi.advanceTimersByTime(89_000)
    })
    expect(screen.getByText(/Delayed location — updated 2 minutes ago/)).toBeInTheDocument()
    expect(map.kindsLastDrawn()).toEqual(['handoff', 'courier'])

    await act(async () => {
      vi.advanceTimersByTime(1_000)
    })
    expect(screen.getByText(/Location unavailable — last reported 2 minutes ago/)).toBeInTheDocument()
    // The handoff stays: an unavailable courier leaves a map with a destination, not a blank one.
    expect(map.kindsLastDrawn()).toEqual(['handoff'])
  })

  /**
   * Sharing never started, Stop was pressed, and a reading that aged out of the server's keeping
   * all arrive as the same absent position. The page therefore says the position is unavailable
   * rather than naming one of the three, which it has no way to tell apart.
   */
  it('names no cause when the server holds no usable position at all', async () => {
    const map = recordingEngine()
    respondWithSnapshot(
      inTransit({ map: { handoff: { latitude: 51.509, longitude: -0.13 }, courier: null } }),
    )

    renderPage(map.engine)

    // Said in the sentence and again in the live region: a courier's position vanishing is one of
    // the two changes on this page worth interrupting a reader for.
    expect(await screen.findAllByText('The courier’s position is not available right now.')).toHaveLength(2)
    await waitFor(() => expect(map.kindsLastDrawn()).toEqual(['handoff']))
  })

  it('withdraws the courier and every trace of location once the delivery is handed over', async () => {
    const map = recordingEngine()
    respondWithSnapshot(snapshotOf('DELIVERED', { completedAt: '2026-08-10T09:42:00.000Z' }))

    renderPage(map.engine)

    expect(await screen.findByRole('heading', { name: 'Delivered' })).toBeInTheDocument()
    expect(screen.getByText(/Handed over at/)).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Courier' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Courier location' })).not.toBeInTheDocument()
    expect(map.mounts).toHaveLength(0)
  })

  /**
   * The Reference survives cancellation because the same page offers a Delivery Team Contact, and a
   * Recipient phoning that number needs something to name the delivery by. The Handoff Address does
   * not: it is the field that says where somebody lives, and nothing is being delivered there now.
   */
  it('shows a cancelled delivery its reference, outcome, time and who to ask, but no address', async () => {
    const map = recordingEngine()
    respondWithSnapshot(
      snapshotOf('CANCELLED', {
        handoffAddressLabel: null,
        completedAt: '2026-08-10T09:42:00.000Z',
        deliveryTeamContact: '+44 20 7946 0000',
      }),
    )

    renderPage(map.engine)

    expect(await screen.findByRole('heading', { name: 'This delivery was cancelled' })).toBeInTheDocument()
    expect(screen.getByText('DG-0042')).toBeInTheDocument()
    expect(screen.getByText(/Cancelled at/)).toBeInTheDocument()
    expect(screen.getByText(/\+44 20 7946 0000/)).toBeInTheDocument()
    expect(screen.queryByText('2 Handoff Road, London')).not.toBeInTheDocument()
    expect(map.mounts).toHaveLength(0)
  })

  it('points a cancelled Recipient back at whoever shared the link when no contact is configured', async () => {
    respondWithSnapshot(snapshotOf('CANCELLED', { handoffAddressLabel: null }))

    renderPage()

    expect(
      await screen.findByText(/contact the delivery team through the channel that shared this link/),
    ).toBeInTheDocument()
  })

  it('keeps the status and freshness content when this deployment has no map configured', async () => {
    const map = recordingEngine()
    respondWithSnapshot(inTransit())

    renderPage(map.engine, '')

    expect(await screen.findByText(/Live location — updated/)).toBeInTheDocument()
    expect(screen.getByText(/The map is unavailable/)).toBeInTheDocument()
    expect(map.mounts).toHaveLength(0)
  })

  it('says the map is unavailable rather than leaving a blank box when the style fails to load', async () => {
    respondWithSnapshot(inTransit())

    renderPage(failingEngine)

    expect(await screen.findByText(/The map is unavailable/)).toBeInTheDocument()
    expect(screen.getByText(/Live location — updated/)).toBeInTheDocument()
  })

  /**
   * The network half of the Issue's fourth acceptance criterion. The engine is given the configured
   * style URL and nothing else, so a tile request built from it cannot carry the grant, the Delivery
   * or the marker the reader is looking at.
   */
  it('hands the map a style URL carrying no grant, no Delivery and no coordinates', async () => {
    const map = recordingEngine()
    respondWithSnapshot(inTransit())

    renderPage(map.engine)
    await waitFor(() => expect(map.mounts).toHaveLength(1))

    const { styleUrl, ...rest } = map.mounts[0]
    expect(styleUrl).toBe(STYLE_URL)
    expect(styleUrl).not.toContain(GRANT_SECRET)
    expect(styleUrl).not.toContain(DELIVERY_ID)
    expect(styleUrl).not.toContain('51.508')
    // Nothing but the markers travels alongside it, so there is no second channel to leak through.
    expect(Object.keys(rest)).toEqual(['markers'])
  })

  it('reads only its snapshot and opt-in state, and never sends the grant in a URL', async () => {
    respondWithSnapshot(inTransit())

    renderPage(recordingEngine().engine)
    await screen.findByRole('heading', { name: 'Your delivery is on the way' })

    // The snapshot is the delivery read; the opt-in section asks the server once whether it may be
    // offered. Both are same-origin GETs, and the grant travels in neither URL.
    await waitFor(() =>
      expect(vi.mocked(fetch).mock.calls.map(([input]) => urlOf(input))).toEqual([
        '/api/tracking/snapshot',
        '/api/tracking/notifications',
      ]),
    )
    vi.mocked(fetch).mock.calls.forEach(([input]) => expect(urlOf(input)).not.toContain(GRANT_SECRET))
  })

  /**
   * React mounts an effect, tears it down and mounts it again in development. Two maps therefore
   * exist at once, and for as long as they shared one element the first one's teardown emptied the
   * element the survivor was drawing into — a blank rectangle in the browser and nothing at all in
   * a test that renders without StrictMode.
   */
  it('survives the double mount React performs in development', async () => {
    const map = recordingEngine()
    respondWithSnapshot(inTransit())

    render(
      <StrictMode>
        <TrackingPage map={{ styleUrl: STYLE_URL, engine: map.engine }} />
      </StrictMode>,
    )
    await waitFor(() => expect(map.surfaces.length).toBeGreaterThan(1))

    const survivors = map.surfaces.filter((surface) => !surface.destroyed)
    expect(survivors).toHaveLength(1)
    expect(survivors[0].element.isConnected).toBe(true)
    // Every element handed out is its own, so no teardown can reach the survivor's.
    expect(new Set(map.surfaces.map((surface) => surface.element)).size).toBe(map.surfaces.length)
  })

  /**
   * The whole point of the stream: a change reaches the page without anybody touching it. What
   * arrives is a hint, and the state on screen still comes from the snapshot fetched because of it.
   */
  it('refetches and shows the new state when a hint says the delivery changed', async () => {
    const updates = fakeUpdates()
    respondWithSnapshot(snapshotOf('ASSIGNED', { courierDisplayName: 'Cory C.' }))

    renderPage(undefined, STYLE_URL, updates.open)
    expect(await screen.findByRole('heading', { name: 'A courier has been assigned' })).toBeInTheDocument()

    respondWithSnapshot(snapshotOf('DELIVERED', { completedAt: '2026-08-10T09:42:00.000Z' }))
    await updates.hint(1)

    expect(screen.getByRole('heading', { name: 'Delivered' })).toBeInTheDocument()
    expect(screen.getByText(/Handed over at/)).toBeInTheDocument()
  })

  it('skips a hint it has already acted on and refetches for one it has not', async () => {
    const updates = fakeUpdates()
    respondWithSnapshot(snapshotOf('ASSIGNED', { courierDisplayName: 'Cory C.' }))
    renderPage(undefined, STYLE_URL, updates.open)
    await screen.findByRole('heading', { name: 'A courier has been assigned' })

    await updates.hint(4)
    const afterFirst = snapshotRequests().length

    // Same version again, then an older one: both describe a snapshot this page already fetched.
    await updates.hint(4)
    await updates.hint(2)
    expect(snapshotRequests()).toHaveLength(afterFirst)

    await updates.hint(5)
    expect(snapshotRequests()).toHaveLength(afterFirst + 1)
  })

  /** A frame nobody can read is still a page asking to be refreshed, so it is refreshed. */
  it('refetches for a hint whose version it could not read', async () => {
    const updates = fakeUpdates()
    respondWithSnapshot(snapshotOf('ASSIGNED', { courierDisplayName: 'Cory C.' }))
    renderPage(undefined, STYLE_URL, updates.open)
    await screen.findByRole('heading', { name: 'A courier has been assigned' })
    const before = snapshotRequests().length

    await updates.hintUnreadable()

    expect(snapshotRequests()).toHaveLength(before + 1)
  })

  /**
   * The Issue's reconnect rule, from the page's side: it does not replay anything and it does not
   * ask what it missed. It fetches the snapshot once, which is the current answer whatever happened
   * while it was away.
   */
  it('fetches the snapshot exactly once each time it connects', async () => {
    const updates = fakeUpdates()
    respondWithSnapshot(snapshotOf('AWAITING_COURIER'))
    renderPage(undefined, STYLE_URL, updates.open)
    await screen.findByRole('heading', { name: 'We’re preparing your delivery' })
    expect(snapshotRequests()).toEqual(['/api/tracking/snapshot'])

    await updates.connect()
    expect(snapshotRequests()).toHaveLength(2)

    await updates.drop(true)
    respondWithSnapshot(snapshotOf('CANCELLED', { handoffAddressLabel: null }))
    await updates.connect()

    expect(snapshotRequests()).toHaveLength(3)
    // The change it never heard about, arriving anyway.
    expect(screen.getByRole('heading', { name: 'This delivery was cancelled' })).toBeInTheDocument()
  })

  it('keeps the delivery and its timestamps on screen while it is reconnecting', async () => {
    const updates = fakeUpdates()
    const map = recordingEngine()
    respondWithSnapshot(inTransit())
    renderPage(map.engine, STYLE_URL, updates.open)
    await screen.findByRole('heading', { name: 'Your delivery is on the way' })

    await updates.drop(true)

    expect(screen.getByRole('heading', { name: 'Your delivery is on the way' })).toBeInTheDocument()
    expect(screen.getByText('Cory C.')).toBeInTheDocument()
    expect(screen.getByText(/Live location — updated/)).toBeInTheDocument()
    expect(screen.getByText('Reconnecting for updates…')).toBeInTheDocument()
  })

  /**
   * Connection and freshness are two different facts and the page says both, because a reader with
   * only one of them will draw the wrong conclusion from it: a live connection is not a promise
   * that the marker is current, and a stale marker is not evidence that the page has stopped
   * working.
   */
  it('says whether it is connected separately from how old the position is', async () => {
    const updates = fakeUpdates()
    vi.useFakeTimers()
    vi.setSystemTime(NOW)
    respondWithSnapshot(inTransit())
    renderPage(recordingEngine().engine, STYLE_URL, updates.open)
    await act(async () => {})
    await updates.connect()

    // Connected, and the reading is fresh.
    expect(screen.getByText('Updating automatically')).toBeInTheDocument()
    expect(screen.getByText(/Live location — updated/)).toBeInTheDocument()

    // Still connected two minutes later, and the position is gone anyway.
    await act(async () => {
      vi.advanceTimersByTime(121_000)
    })
    expect(screen.getByText('Updating automatically')).toBeInTheDocument()
    expect(screen.getByText(/Location unavailable — last reported/)).toBeInTheDocument()
  })

  /**
   * A refused stream is not a refused link. The browser stops retrying, the page says so and keeps
   * everything it already has, and a reload is still a complete answer.
   */
  it('keeps the delivery and asks for a reload when the browser gives the stream up', async () => {
    const updates = fakeUpdates()
    respondWithSnapshot(snapshotOf('ASSIGNED', { courierDisplayName: 'Cory C.' }))
    renderPage(undefined, STYLE_URL, updates.open)
    await screen.findByRole('heading', { name: 'A courier has been assigned' })

    await updates.drop(false)

    expect(screen.getByRole('heading', { name: 'A courier has been assigned' })).toBeInTheDocument()
    expect(screen.getByText(/Not updating automatically/)).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  /**
   * A reconnect happens exactly when the network has been unreliable, so the refetch that follows
   * one is the read most likely to fail. Letting it replace the delivery with "could not reach the
   * delivery service" would throw away a good snapshot over a moment the page was already handling.
   */
  it('does not replace a delivery it is showing when its own refetch cannot reach the server', async () => {
    const updates = fakeUpdates()
    respondWithSnapshot(inTransit())
    renderPage(recordingEngine().engine, STYLE_URL, updates.open)
    await screen.findByRole('heading', { name: 'Your delivery is on the way' })

    vi.mocked(fetch).mockRejectedValue(new TypeError('Failed to fetch'))
    await updates.connect()

    expect(screen.getByRole('heading', { name: 'Your delivery is on the way' })).toBeInTheDocument()
    expect(screen.getByText(/Live location — updated/)).toBeInTheDocument()
    expect(screen.queryByText(/Could not reach the delivery service/)).not.toBeInTheDocument()
  })

  /** A link that expired while the page was open is still one refusal, arriving by refetch. */
  it('shows the single refusal when a refetch finds the link is gone', async () => {
    const updates = fakeUpdates()
    respondWithSnapshot(snapshotOf('ASSIGNED', { courierDisplayName: 'Cory C.' }))
    renderPage(undefined, STYLE_URL, updates.open)
    await screen.findByRole('heading', { name: 'A courier has been assigned' })

    vi.mocked(fetch).mockResolvedValue(problemResponse('tracking-link-unavailable', 404))
    await updates.hint(1)

    expect(screen.getByRole('alert')).toHaveTextContent('This tracking link is no longer available')
  })

  it('closes the stream when the page goes away', async () => {
    const updates = fakeUpdates()
    respondWithSnapshot(snapshotOf('AWAITING_COURIER'))
    const view = renderPage(undefined, STYLE_URL, updates.open)
    await screen.findByRole('heading', { name: 'We’re preparing your delivery' })

    view.unmount()

    expect(updates.closeCount()).toBe(1)
  })

  /**
   * jsdom has no layout, so this cannot prove the page looks right on a phone — the PR's mobile
   * screenshots are that evidence. What it does prove is that nothing on the page is conditional on
   * viewport width, so a narrow screen loses no content.
   */
  it('renders the whole delivery on a narrow mobile viewport', async () => {
    const map = recordingEngine()
    respondWithSnapshot(inTransit())
    window.innerWidth = 360
    window.innerHeight = 640
    window.dispatchEvent(new Event('resize'))

    renderPage(map.engine)

    expect(await screen.findByRole('heading', { name: 'Your delivery is on the way' })).toBeInTheDocument()
    expect(screen.getByText('2 Handoff Road, London')).toBeInTheDocument()
    expect(screen.getByText('Cory C.')).toBeInTheDocument()
    expect(screen.getByText(/Live location — updated/)).toBeInTheDocument()
    expect(screen.getByRole('img', { name: /Map of the handoff address/ })).toBeInTheDocument()
  })
})
