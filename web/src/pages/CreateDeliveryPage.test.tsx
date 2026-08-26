import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CreateDeliveryPage } from './CreateDeliveryPage'
import {
  jsonResponse,
  problemResponse,
  renderWithProviders,
  requestBodyOf,
  respondWith,
} from '../testing/support'

async function fillInTheForm() {
  await userEvent.type(screen.getByLabelText('Delivery reference'), 'DG-1001')
  const [pickupLabel, handoffLabel] = screen.getAllByLabelText('Address')
  const [pickupLatitude, handoffLatitude] = screen.getAllByLabelText('Latitude')
  const [pickupLongitude, handoffLongitude] = screen.getAllByLabelText('Longitude')

  await userEvent.type(pickupLabel, 'Warehouse 4, Riverside Estate')
  await userEvent.type(pickupLatitude, '51.5074')
  await userEvent.type(pickupLongitude, '-0.1278')
  await userEvent.type(handoffLabel, 'Flat 2, 14 Notional Row')
  await userEvent.type(handoffLatitude, '51.5033')
  await userEvent.type(handoffLongitude, '-0.1195')
}

describe('CreateDeliveryPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('sends the reference and both addresses', async () => {
    respondWith(() => jsonResponse({ id: 'created-id' }, 201))
    renderWithProviders(<CreateDeliveryPage />)

    await fillInTheForm()
    await userEvent.click(screen.getByRole('button', { name: 'Create delivery' }))

    const call = vi.mocked(fetch).mock.calls.find(([url]) => url === '/api/deliveries')
    expect(call).toBeDefined()
    expect(requestBodyOf(call as [RequestInfo | URL, RequestInit])).toEqual({
      reference: 'DG-1001',
      pickup: {
        addressLabel: 'Warehouse 4, Riverside Estate',
        latitude: 51.5074,
        longitude: -0.1278,
      },
      handoff: { addressLabel: 'Flat 2, 14 Notional Row', latitude: 51.5033, longitude: -0.1195 },
    })
  })

  it('marks the pickup address as internal', () => {
    renderWithProviders(<CreateDeliveryPage />)

    const pickup = screen.getByRole('group', { name: /Pickup address/ })
    expect(pickup).toHaveTextContent('internal')
  })

  it('shows the server validation message next to the field it belongs to', async () => {
    respondWith(() =>
      problemResponse('invalid-request', 400, {
        errors: [
          { field: 'pickup.latitude', message: 'must be between -90 and 90' },
          { field: 'reference', message: 'is required' },
        ],
      }),
    )
    renderWithProviders(<CreateDeliveryPage />)

    await fillInTheForm()
    await userEvent.click(screen.getByRole('button', { name: 'Create delivery' }))

    const latitudeError = await screen.findByText('Latitude must be between -90 and 90')
    expect(screen.getAllByLabelText('Latitude')[0]).toHaveAttribute('aria-describedby', latitudeError.id)
    expect(screen.getAllByLabelText('Latitude')[0]).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByText('Delivery reference is required')).toBeInTheDocument()
  })

  it('explains a reference that is already in use', async () => {
    respondWith(() => problemResponse('delivery-reference-taken', 409))
    renderWithProviders(<CreateDeliveryPage />)

    await fillInTheForm()
    await userEvent.click(screen.getByRole('button', { name: 'Create delivery' }))

    expect(await screen.findByText(/Another delivery already uses that reference/)).toBeVisible()
  })
})
