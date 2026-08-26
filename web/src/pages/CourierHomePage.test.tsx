import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CourierHomePage } from './CourierHomePage'
import {
  jsonResponse,
  noContentResponse,
  problemResponse,
  renderWithProviders,
  requestBodyOf,
  respondWith,
  urlOf,
} from '../testing/support'

const GENERATION = 'a3f1c2d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d'

const offDuty = {
  displayName: 'Cory the Courier',
  onDuty: false,
  onDutyChangedAt: null,
  sharing: null,
  location: { freshness: 'UNAVAILABLE', recordedAt: null, accuracyMetres: null },
}

const startedSession = {
  generation: GENERATION,
  reportingSecret: 'one-time-reporting-secret',
  startedAt: '2026-08-10T09:00:00Z',
}

const accepted = {
  outcome: 'ACCEPTED',
  location: { freshness: 'LIVE', recordedAt: '2026-08-10T09:00:05Z', accuracyMetres: 12 },
}

const assignedDelivery = {
  id: '5f2d0b1e-3f6e-4a1f-9f1a-9a2b3c4d5e6f',
  reference: 'DG-1001',
  state: 'ASSIGNED',
  version: 1,
  pickupAddressLabel: 'Warehouse 4',
  handoffAddressLabel: 'Flat 2, 14 Notional Row',
}

let watchPosition = vi.fn()
let clearWatch = vi.fn()
let deliverPosition: PositionCallback | null = null
let deliverError: PositionErrorCallback | null = null

/** jsdom has no Geolocation, so the test supplies one and keeps hold of its callbacks. */
function stubGeolocation() {
  deliverPosition = null
  deliverError = null
  watchPosition = vi.fn((success: PositionCallback, failure?: PositionErrorCallback | null) => {
    deliverPosition = success
    deliverError = failure ?? null
    return 7
  })
  clearWatch = vi.fn()
  Object.defineProperty(navigator, 'geolocation', {
    value: { watchPosition, clearWatch, getCurrentPosition: vi.fn() },
    configurable: true,
  })
}

function respondForCourier(courier: unknown = offDuty) {
  respondWith((url, method) => {
    if (url.endsWith('/deliveries/current')) {
      return noContentResponse()
    }
    if (url.endsWith('/location-sharing')) {
      return method === 'POST' ? jsonResponse(startedSession, 201) : noContentResponse()
    }
    if (url.endsWith('/location-reports')) {
      return jsonResponse(accepted)
    }
    if (url.endsWith('/duty')) {
      return jsonResponse({ ...(courier as object), onDuty: true, onDutyChangedAt: '2026-08-10T09:00:00Z' })
    }
    return jsonResponse(courier)
  })
}

function callsTo(path: string): [RequestInfo | URL, RequestInit | undefined][] {
  return vi
    .mocked(fetch)
    .mock.calls.filter(([url]) => urlOf(url).endsWith(path))
    .map(([url, init]) => [url, init])
}

async function fix(latitude: number, timestamp = Date.parse('2026-08-10T09:00:05Z')) {
  await act(async () => {
    deliverPosition?.({
      coords: { latitude, longitude: -0.1278, accuracy: 12 },
      timestamp,
    } as unknown as GeolocationPosition)
  })
}

function setPageHidden(hidden: boolean) {
  Object.defineProperty(document, 'hidden', { value: hidden, configurable: true })
}

async function announceVisibilityChange() {
  await act(async () => {
    document.dispatchEvent(new Event('visibilitychange'))
  })
}

async function startSharing() {
  await userEvent.click(await screen.findByRole('button', { name: 'Start sharing' }))
  await screen.findByRole('button', { name: 'Stop sharing' })
}

describe('CourierHomePage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
    stubGeolocation()
    setPageHidden(false)
  })

  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
    vi.useRealTimers()
    Reflect.deleteProperty(navigator, 'geolocation')
    Reflect.deleteProperty(document, 'hidden')
  })

  it('asks the browser for a position only after Start', async () => {
    respondForCourier()
    renderWithProviders(<CourierHomePage />)

    expect(await screen.findByRole('heading', { name: 'Courier workspace' })).toBeInTheDocument()
    expect(watchPosition).not.toHaveBeenCalled()

    await startSharing()

    expect(watchPosition).toHaveBeenCalledTimes(1)
    expect(callsTo('/location-sharing')).toHaveLength(1)
  })

  it('reports the first position promptly once sharing has started', async () => {
    respondForCourier()
    renderWithProviders(<CourierHomePage />)
    await startSharing()

    await fix(51.5074)

    expect(callsTo('/location-reports')).toHaveLength(1)
    expect(requestBodyOf(callsTo('/location-reports')[0])).toMatchObject({
      generation: GENERATION,
      reportingSecret: 'one-time-reporting-secret',
      latitude: 51.5074,
      longitude: -0.1278,
      accuracyMetres: 12,
      recordedAt: '2026-08-10T09:00:05.000Z',
    })
  })

  it('stops collecting and sends nothing while the page is in the background', async () => {
    respondForCourier()
    renderWithProviders(<CourierHomePage />)
    await startSharing()

    setPageHidden(true)
    await announceVisibilityChange()

    // The watch is cleared, so the browser is not asked for positions the product may not have.
    expect(clearWatch).toHaveBeenCalledWith(7)
    // And a browser that keeps calling back anyway still gets nothing reported for it.
    await fix(51.5074)
    expect(callsTo('/location-reports')).toHaveLength(0)
    expect(await screen.findByText('Sharing interrupted')).toBeInTheDocument()
  })

  it('sends only the newest position when the page comes back', async () => {
    respondForCourier()
    renderWithProviders(<CourierHomePage />)
    await startSharing()

    setPageHidden(true)
    await announceVisibilityChange()
    await fix(51.5, Date.parse('2026-08-10T09:00:05Z'))
    await fix(51.6, Date.parse('2026-08-10T09:00:15Z'))

    setPageHidden(false)
    await announceVisibilityChange()

    // Whatever readings a hidden page ended up holding are not a backlog to upload: only the
    // newest is a position, and the ones before it are the route the product refuses to keep.
    expect(watchPosition).toHaveBeenCalledTimes(2)
    expect(callsTo('/location-reports')).toHaveLength(1)
    expect(requestBodyOf(callsTo('/location-reports')[0])).toMatchObject({
      latitude: 51.6,
      recordedAt: '2026-08-10T09:00:15.000Z',
    })
  })

  it('starts in Sharing off after a reload that lost the reporting secret', async () => {
    respondForCourier({
      ...offDuty,
      sharing: { startedAt: '2026-08-10T09:00:00Z' },
      location: { freshness: 'LIVE', recordedAt: '2026-08-10T09:00:05Z', accuracyMetres: 12 },
    })
    renderWithProviders(<CourierHomePage />)

    expect(await screen.findByText('Sharing off')).toBeInTheDocument()
    expect(watchPosition).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Start sharing' })).toBeInTheDocument()
    // The earlier session is still the Courier's to end, even from a page that cannot report.
    expect(screen.getByRole('button', { name: 'Stop sharing' })).toBeInTheDocument()
  })

  it('reads the new session back before it collects anything', async () => {
    // Starting a session tells the server to forget whatever position it was holding, so the page
    // has to read itself back or it would keep showing a position that no longer exists. The order
    // is what this is about: that read leaves while the server holds nothing, and if it is still in
    // flight when the first report is answered, it lands afterwards and overwrites a live position
    // with the emptiness it was sent to observe. Nothing corrects it — the next reading is only
    // sent if the device produces one — so the Courier is told indefinitely that nothing is being
    // shared while their position is on the Recipient's map.
    let answerTheReadBack: (() => void) | null = null
    let courierReads = 0
    const acceptedNow = {
      outcome: 'ACCEPTED',
      // Relative, because the page ages this itself and a fixture instant would be hours stale.
      location: { freshness: 'LIVE', recordedAt: new Date().toISOString(), accuracyMetres: 12 },
    }

    vi.mocked(fetch).mockImplementation((input, init) => {
      const url = urlOf(input)
      const method = (init?.method ?? 'GET').toUpperCase()
      if (url.endsWith('/deliveries/current')) {
        return Promise.resolve(noContentResponse())
      }
      if (url.endsWith('/location-sharing')) {
        return Promise.resolve(method === 'POST' ? jsonResponse(startedSession, 201) : noContentResponse())
      }
      if (url.endsWith('/location-reports')) {
        return Promise.resolve(jsonResponse(acceptedNow))
      }
      courierReads += 1
      if (courierReads === 1) {
        return Promise.resolve(jsonResponse(offDuty))
      }
      return new Promise<Response>((resolve) => {
        answerTheReadBack = () => resolve(jsonResponse(offDuty))
      })
    })

    renderWithProviders(<CourierHomePage />)
    await startSharing()

    expect(watchPosition).not.toHaveBeenCalled()

    await act(async () => answerTheReadBack?.())
    await waitFor(() => expect(watchPosition).toHaveBeenCalledTimes(1))
    await fix(51.5074, Date.parse(acceptedNow.location.recordedAt))

    expect(await screen.findByText(/^Live — measured/)).toBeInTheDocument()
  })

  it('stops sharing and asks the server to forget the position', async () => {
    respondForCourier()
    renderWithProviders(<CourierHomePage />)
    await startSharing()

    await userEvent.click(screen.getByRole('button', { name: 'Stop sharing' }))

    expect(clearWatch).toHaveBeenCalledWith(7)
    expect(callsTo('/location-sharing').map(([, init]) => init?.method)).toContain('DELETE')
    expect(await screen.findByText('Sharing off')).toBeInTheDocument()
  })

  it('ends sharing when the browser refuses permission', async () => {
    respondForCourier()
    renderWithProviders(<CourierHomePage />)
    await startSharing()

    await act(async () => {
      deliverError?.({ code: 1, PERMISSION_DENIED: 1 } as unknown as GeolocationPositionError)
    })

    expect(await screen.findByText('Sharing off')).toBeInTheDocument()
    expect(callsTo('/location-sharing').map(([, init]) => init?.method)).toContain('DELETE')
    expect(screen.getByRole('alert')).toHaveTextContent('Location permission was refused')
  })

  it('returns to Sharing off when the server no longer knows the session', async () => {
    respondWith((url, method) => {
      if (url.endsWith('/location-reports')) {
        return problemResponse('location-sharing-ended', 409)
      }
      if (url.endsWith('/location-sharing')) {
        return method === 'POST' ? jsonResponse(startedSession, 201) : noContentResponse()
      }
      if (url.endsWith('/deliveries/current')) {
        return noContentResponse()
      }
      return jsonResponse(offDuty)
    })
    renderWithProviders(<CourierHomePage />)
    await startSharing()

    await fix(51.5074)

    expect(await screen.findByText('Sharing off')).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('The server ended this sharing session')
  })

  it('changes duty without touching location sharing', async () => {
    respondForCourier()
    renderWithProviders(<CourierHomePage />)

    await userEvent.click(await screen.findByRole('button', { name: 'Go on duty' }))

    expect(requestBodyOf(callsTo('/duty')[0])).toEqual({ onDuty: true })
    expect(await screen.findByText('On duty')).toBeInTheDocument()
    expect(watchPosition).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Start sharing' })).toBeInTheDocument()
  })

  it('shows the current Delivery and requires explicit pickup and handoff confirmations', async () => {
    let current: typeof assignedDelivery | undefined = assignedDelivery
    respondWith((url, method) => {
      if (url.endsWith('/deliveries/current')) {
        return current === undefined ? noContentResponse() : jsonResponse(current)
      }
      if (url.endsWith('/pickup') && method === 'POST') {
        current = { ...assignedDelivery, state: 'IN_TRANSIT', version: 2 }
        return noContentResponse()
      }
      if (url.endsWith('/handoff') && method === 'POST') {
        current = undefined
        return noContentResponse()
      }
      return jsonResponse(offDuty)
    })
    renderWithProviders(<CourierHomePage />)

    expect(await screen.findByRole('heading', { name: 'Current Delivery' })).toBeInTheDocument()
    expect(await screen.findByText('DG-1001')).toBeInTheDocument()
    // The reskin shows the state as a status chip and both ends of the route as an address pair.
    expect(screen.getByText('Assigned — pickup not yet confirmed')).toBeInTheDocument()
    expect(screen.getByText('Warehouse 4')).toBeInTheDocument()
    expect(screen.getByText('Flat 2, 14 Notional Row')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Confirm pickup' }))

    const pickup = callsTo('/pickup')[0]
    expect(requestBodyOf(pickup)).toMatchObject({ expectedVersion: 1 })
    expect(await screen.findByRole('button', { name: 'Confirm handoff' })).toBeInTheDocument()
    // Confirming pickup moves the chip to the In transit state.
    expect(screen.getByText('In transit')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Confirm handoff' }))

    const handoff = callsTo('/handoff')[0]
    expect(requestBodyOf(handoff)).toMatchObject({ expectedVersion: 2 })
    expect(await screen.findByText('No Delivery is currently assigned to you.')).toBeInTheDocument()
  })

  it('counts down to the moment the server drops the position', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.setSystemTime(new Date('2026-08-10T09:01:00Z'))
    respondForCourier({
      ...offDuty,
      location: { freshness: 'DELAYED', recordedAt: '2026-08-10T09:00:20Z', accuracyMetres: 12 },
    })
    renderWithProviders(<CourierHomePage />)

    expect(await screen.findByText(/Delayed — measured 40 seconds ago, removed in 1:20/)).toBeInTheDocument()
  })
})
