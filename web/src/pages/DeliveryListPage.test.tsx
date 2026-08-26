import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, screen } from '@testing-library/react'
import { DeliveryListPage } from './DeliveryListPage'
import { jsonResponse, problemResponse, renderWithProviders, respondWith } from '../testing/support'

const summary = {
  id: '5f2d0b1e-3f6e-4a1f-9f1a-9a2b3c4d5e6f',
  reference: 'DG-1001',
  state: 'AWAITING_COURIER',
  version: 0,
  pickupAddressLabel: 'Warehouse 4, Riverside Estate',
  handoffAddressLabel: 'Flat 2, 14 Notional Row',
  createdAt: '2026-08-10T09:00:00Z',
  updatedAt: '2026-08-10T09:00:00Z',
}

describe('DeliveryListPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('shows a loading state before the deliveries arrive', () => {
    vi.mocked(fetch).mockReturnValue(new Promise(() => {}))

    renderWithProviders(<DeliveryListPage />)

    expect(screen.getByRole('status')).toHaveTextContent(/loading deliveries/i)
  })

  it('invites the first delivery when there are none', async () => {
    respondWith(() => jsonResponse([]))

    renderWithProviders(<DeliveryListPage />)

    expect(await screen.findByText(/No deliveries yet/)).toHaveAttribute('role', 'status')
  })

  it('lists each delivery with its status and a link to the detail', async () => {
    respondWith(() => jsonResponse([summary]))

    renderWithProviders(<DeliveryListPage />)

    const link = await screen.findByRole('link', { name: 'DG-1001' })
    expect(link).toHaveAttribute('href', `/deliveries/${summary.id}`)
    expect(screen.getByText('Awaiting courier')).toBeInTheDocument()
    expect(screen.getByText('Flat 2, 14 Notional Row')).toBeInTheDocument()
  })

  it('reports a failed load', async () => {
    respondWith(() => problemResponse('unknown-error', 500))

    renderWithProviders(<DeliveryListPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load deliveries.')
  })
})
