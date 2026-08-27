import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { DeliveryDetailPage } from './DeliveryDetailPage'
import {
  jsonResponse,
  noContentResponse,
  problemResponse,
  renderWithProviders,
  requestBodyOf,
  respondWith,
  urlOf,
} from '../testing/support'

const DELIVERY_ID = '5f2d0b1e-3f6e-4a1f-9f1a-9a2b3c4d5e6f'

const awaitingCourier = {
  id: DELIVERY_ID,
  reference: 'DG-1001',
  state: 'AWAITING_COURIER',
  version: 0,
  pickup: { addressLabel: 'Warehouse 4', latitude: 51.5074, longitude: -0.1278 },
  handoff: { addressLabel: 'Flat 2, 14 Notional Row', latitude: 51.5033, longitude: -0.1195 },
  createdAt: '2026-08-10T09:00:00Z',
  updatedAt: '2026-08-10T09:00:00Z',
  transitions: [
    {
      previousState: null,
      nextState: 'AWAITING_COURIER',
      actorDisplayName: 'Dana the Dispatcher',
      reasonCode: null,
      reasonNote: null,
      occurredAt: '2026-08-10T09:00:00Z',
    },
  ],
  assignment: null,
}

const cancelled = { ...awaitingCourier, state: 'CANCELLED', version: 1 }

const assigned = {
  ...awaitingCourier,
  state: 'ASSIGNED',
  version: 1,
  assignment: {
    courierId: '50f3cc79-c56d-47b7-aacf-4c9ea0eaa002',
    courierDisplayName: 'Cory the Courier',
    assignedAt: '2026-08-10T09:05:00Z',
  },
}

const delivered = { ...assigned, state: 'DELIVERED', version: 3 }

const systemWithProof = { application: 'delivery-glance', status: 'ok', proofCaptureEnabled: true }

const proofSet = {
  artifacts: [
    {
      kind: 'PHOTO',
      status: 'READY',
      capturedAt: '2026-08-10T09:42:00Z',
      processedAt: '2026-08-10T09:42:05Z',
      thumbnailUrl: 'https://bucket.s3.example/thumb.jpg?sig=1',
      fullUrl: 'https://bucket.s3.example/full.jpg?sig=2',
    },
    {
      kind: 'SIGNATURE',
      status: 'PENDING',
      capturedAt: '2026-08-10T09:42:00Z',
      processedAt: null,
      thumbnailUrl: null,
      fullUrl: null,
    },
  ],
}

const recommendation = {
  calculatedAt: '2026-08-10T09:04:00Z',
  candidates: [
    {
      courierId: '50f3cc79-c56d-47b7-aacf-4c9ea0eaa002',
      displayName: 'Cory the Courier',
      distanceMetres: 432.4,
    },
  ],
}

const emptyRecommendation = { calculatedAt: '2026-08-10T09:04:00Z', candidates: [] }

// The raw capability the copy endpoint returns. The test's whole point is that this string reaches
// the clipboard and never the DOM, so it is deliberately distinctive.
const copiedLink = {
  url: '/track#t=raw-capability-token-must-never-render',
  expiresAt: '2026-08-17T09:00:00Z',
}

function renderDetail() {
  renderWithProviders(
    <Routes>
      <Route path="/deliveries/:id" element={<DeliveryDetailPage />} />
    </Routes>,
    { route: `/deliveries/${DELIVERY_ID}` },
  )
}

function cancelCalls() {
  return vi.mocked(fetch).mock.calls.filter(([url]) => urlOf(url).endsWith('/cancel'))
}

describe('DeliveryDetailPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
    // The clipboard test defines navigator.clipboard; drop it so the stub cannot leak into later tests.
    Reflect.deleteProperty(navigator, 'clipboard')
  })

  it('shows the delivery, its route and its history', async () => {
    respondWith((url) =>
      jsonResponse(url.endsWith('/courier-recommendations') ? emptyRecommendation : awaitingCourier),
    )

    renderDetail()

    expect(await screen.findByRole('heading', { name: 'DG-1001' })).toBeInTheDocument()
    expect(screen.getByText('Flat 2, 14 Notional Row (51.5033, -0.1195)')).toBeInTheDocument()
    expect(screen.getByRole('listitem')).toHaveTextContent('Awaiting courier by Dana the Dispatcher')
  })

  it('shows a delivered proof to the Dispatcher: a ready thumbnail that links to the full image', async () => {
    respondWith((url) => {
      if (url.endsWith('/api/system')) {
        return jsonResponse(systemWithProof)
      }
      if (url.endsWith('/proof')) {
        return jsonResponse(proofSet)
      }
      return jsonResponse(delivered)
    })

    renderDetail()

    expect(await screen.findByRole('heading', { name: 'Proof of delivery' })).toBeInTheDocument()
    const openFull = await screen.findByRole('link', { name: 'Delivery photo — open full image' })
    expect(openFull).toHaveAttribute('href', 'https://bucket.s3.example/full.jpg?sig=2')
    expect(screen.getByAltText('Delivery photo — open full image')).toHaveAttribute(
      'src',
      'https://bucket.s3.example/thumb.jpg?sig=1',
    )
    // The signature is still processing, so it is a status and no image to load.
    expect(screen.getByText('Processing…')).toBeInTheDocument()
  })

  it('offers no proof panel until a delivery is delivered', async () => {
    respondWith((url) => {
      if (url.endsWith('/api/system')) {
        return jsonResponse(systemWithProof)
      }
      if (url.endsWith('/courier-recommendations')) {
        return jsonResponse(emptyRecommendation)
      }
      if (url.endsWith('/proof')) {
        return jsonResponse(proofSet)
      }
      return jsonResponse(awaitingCourier)
    })

    renderDetail()

    await screen.findByRole('heading', { name: 'DG-1001' })
    expect(screen.queryByRole('heading', { name: 'Proof of delivery' })).not.toBeInTheDocument()
    expect(fetch).not.toHaveBeenCalledWith(expect.stringMatching(/\/proof$/), expect.anything())
  })

  it('shows a fresh nearest recommendation and directly assigns the selected Courier', async () => {
    let current: typeof awaitingCourier | typeof assigned = awaitingCourier
    respondWith((url, method) => {
      if (url.endsWith('/courier-recommendations')) {
        return jsonResponse(recommendation)
      }
      if (url.endsWith('/assignment') && method === 'POST') {
        current = assigned
        return noContentResponse()
      }
      return jsonResponse(current)
    })
    renderDetail()

    expect(await screen.findByText('Cory the Courier')).toBeInTheDocument()
    expect(screen.getByText(/km from pickup/)).toHaveTextContent('0.4 km from pickup')
    await userEvent.click(screen.getByRole('button', { name: 'Direct assign Cory the Courier' }))

    const call = vi.mocked(fetch).mock.calls.find(([url]) => String(url).endsWith('/assignment'))
    expect(call?.[0]).toBe(`/api/deliveries/${DELIVERY_ID}/assignment`)
    expect(requestBodyOf(call as [RequestInfo | URL, RequestInit])).toMatchObject({
      courierId: recommendation.candidates[0].courierId,
      expectedVersion: 0,
    })
    expect(await screen.findByText(/Assigned to/)).toHaveTextContent('Assigned to Cory the Courier')
  })

  it('says so when another Dispatcher won the Delivery, rather than showing their result as ours', async () => {
    // The losing half of the race that AssignmentConcurrencyTest proves on the server. What this
    // page must not do is answer a refused press by quietly redrawing itself as an assigned
    // Delivery: the Courier's name would appear exactly where it appears on a success, and the
    // Dispatcher who pressed would have no way to tell that somebody else put it there.
    let current: typeof awaitingCourier | typeof assigned = awaitingCourier
    respondWith((url, method) => {
      if (url.endsWith('/courier-recommendations')) {
        return jsonResponse(recommendation)
      }
      if (url.endsWith('/assignment') && method === 'POST') {
        // Refused *and* superseded, which is the real shape of losing: the read that follows finds
        // the winner's Delivery.
        current = assigned
        return problemResponse('assignment-delivery-changed', 409)
      }
      return jsonResponse(current)
    })
    renderDetail()

    await userEvent.click(await screen.findByRole('button', { name: 'Direct assign Cory the Courier' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Another assignment changed this Delivery. Reload it to see the winner.',
    )
    expect(screen.getByText(/Assigned to/)).toHaveTextContent('Assigned to Cory the Courier')
  })

  it('cancels with the loaded version and a reason', async () => {
    let current: typeof awaitingCourier = awaitingCourier
    respondWith((url) => {
      if (url.endsWith('/courier-recommendations')) {
        return jsonResponse(emptyRecommendation)
      }
      if (url.endsWith('/cancel')) {
        current = cancelled
      }
      return jsonResponse(current)
    })
    renderDetail()
    await screen.findByRole('button', { name: 'Cancel delivery' })

    await userEvent.selectOptions(screen.getByLabelText('Reason'), 'ITEM_UNAVAILABLE_AT_PICKUP')
    await userEvent.click(screen.getByRole('button', { name: 'Cancel delivery' }))

    const call = cancelCalls().at(0)
    expect(call?.[0]).toBe(`/api/deliveries/${DELIVERY_ID}/cancel`)
    expect(requestBodyOf(call as [RequestInfo | URL, RequestInit])).toMatchObject({
      expectedVersion: 0,
      reason: 'ITEM_UNAVAILABLE_AT_PICKUP',
      note: null,
    })
    expect(await screen.findByText(/final state and cannot be changed/)).toBeVisible()
  })

  it('reuses the same command identifier when a cancellation is retried', async () => {
    respondWith((url) =>
      url.endsWith('/courier-recommendations')
        ? jsonResponse(emptyRecommendation)
        : url.endsWith('/cancel')
          ? problemResponse('unknown-error', 503)
          : jsonResponse(awaitingCourier),
    )
    renderDetail()
    await screen.findByRole('button', { name: 'Cancel delivery' })

    await userEvent.click(screen.getByRole('button', { name: 'Cancel delivery' }))
    await screen.findByRole('alert')
    await userEvent.click(screen.getByRole('button', { name: 'Cancel delivery' }))

    expect(cancelCalls()).toHaveLength(2)
    const [first, second] = cancelCalls().map(
      (call) => requestBodyOf(call as [RequestInfo | URL, RequestInit]) as { commandId: string },
    )
    expect(first.commandId).toBe(second.commandId)
  })

  it('explains a version conflict instead of overwriting the delivery', async () => {
    respondWith((url) =>
      url.endsWith('/courier-recommendations')
        ? jsonResponse(emptyRecommendation)
        : url.endsWith('/cancel')
          ? problemResponse('delivery-version-conflict', 409, {
              currentState: 'CANCELLED',
              currentVersion: 1,
            })
          : jsonResponse(awaitingCourier),
    )
    renderDetail()
    await screen.findByRole('button', { name: 'Cancel delivery' })

    await userEvent.click(screen.getByRole('button', { name: 'Cancel delivery' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('This delivery changed in another window.')
  })

  it('offers no cancellation once the delivery is final', async () => {
    respondWith(() => jsonResponse(cancelled))

    renderDetail()

    expect(await screen.findByText(/final state and cannot be changed/)).toHaveAttribute('role', 'status')
    expect(screen.queryByRole('button', { name: 'Cancel delivery' })).not.toBeInTheDocument()
  })

  it('copies the tracking link to the clipboard without ever rendering the raw URL', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
    respondWith((url) =>
      url.endsWith('/courier-recommendations')
        ? jsonResponse(emptyRecommendation)
        : url.endsWith('/tracking-link/copy')
          ? jsonResponse(copiedLink)
          : jsonResponse(awaitingCourier),
    )
    renderDetail()

    await userEvent.click(await screen.findByRole('button', { name: 'Copy tracking link' }))

    const call = vi.mocked(fetch).mock.calls.find(([url]) => urlOf(url).endsWith('/tracking-link/copy'))
    const [copyUrl, copyInit] = call as [RequestInfo | URL, RequestInit]
    expect(copyUrl).toBe(`/api/deliveries/${DELIVERY_ID}/tracking-link/copy`)
    expect(copyInit.method).toBe('POST')
    expect(writeText).toHaveBeenCalledWith(copiedLink.url)
    expect(await screen.findByText(/^Copied · expires/)).toBeVisible()
    // The one invariant of the whole issue: the raw capability is on the clipboard, not the page.
    expect(document.body).not.toHaveTextContent(copiedLink.url)
  })

  it('offers no copy control once the delivery is final', async () => {
    respondWith(() => jsonResponse(cancelled))

    renderDetail()

    expect(await screen.findByRole('heading', { name: 'Tracking link' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Copy tracking link' })).not.toBeInTheDocument()
  })

  it('reports a delivery that does not exist', async () => {
    respondWith(() => problemResponse('delivery-not-found', 404))

    renderDetail()

    expect(await screen.findByRole('alert')).toHaveTextContent('That delivery does not exist.')
  })
})
