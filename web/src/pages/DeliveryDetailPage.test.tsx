import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { DeliveryDetailPage } from './DeliveryDetailPage'
import {
  jsonResponse,
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
  handoff: { addressLabel: 'Flat 2, 14 Elm Row', latitude: 51.5033, longitude: -0.1195 },
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
}

const cancelled = { ...awaitingCourier, state: 'CANCELLED', version: 1 }

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
  })

  it('shows the delivery, its route and its history', async () => {
    respondWith(() => jsonResponse(awaitingCourier))

    renderDetail()

    expect(await screen.findByRole('heading', { name: 'DG-1001' })).toBeInTheDocument()
    expect(screen.getByText('Flat 2, 14 Elm Row (51.5033, -0.1195)')).toBeInTheDocument()
    expect(screen.getByRole('listitem')).toHaveTextContent('Awaiting courier by Dana the Dispatcher')
  })

  it('cancels with the loaded version and a reason', async () => {
    let current: typeof awaitingCourier = awaitingCourier
    respondWith((url) => {
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
      url.endsWith('/cancel')
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
      url.endsWith('/cancel')
        ? problemResponse('delivery-version-conflict', 409, {
            currentState: 'CANCELLED',
            currentVersion: 1,
          })
        : jsonResponse(awaitingCourier),
    )
    renderDetail()
    await screen.findByRole('button', { name: 'Cancel delivery' })

    await userEvent.click(screen.getByRole('button', { name: 'Cancel delivery' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'This delivery changed in another window.',
    )
  })

  it('offers no cancellation once the delivery is final', async () => {
    respondWith(() => jsonResponse(cancelled))

    renderDetail()

    expect(await screen.findByText(/final state and cannot be changed/)).toHaveAttribute(
      'role',
      'status',
    )
    expect(screen.queryByRole('button', { name: 'Cancel delivery' })).not.toBeInTheDocument()
  })

  it('reports a delivery that does not exist', async () => {
    respondWith(() => problemResponse('delivery-not-found', 404))

    renderDetail()

    expect(await screen.findByRole('alert')).toHaveTextContent('That delivery does not exist.')
  })
})
