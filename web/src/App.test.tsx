import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AppRoutes } from './App'
import {
  jsonResponse,
  noContentResponse,
  problemResponse,
  renderWithProviders,
  respondWith,
} from './testing/support'

const dispatcherSession = { displayName: 'Dana the Dispatcher', role: 'DISPATCHER' }

const courierSession = { displayName: 'Cory the Courier', role: 'COURIER' }

const offDutyCourier = {
  displayName: 'Cory the Courier',
  onDuty: false,
  onDutyChangedAt: null,
  sharing: null,
  location: { freshness: 'UNAVAILABLE', recordedAt: null, accuracyMetres: null },
}

describe('AppRoutes', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('leaves a signed-in dispatcher on the delivery list', async () => {
    respondWith((url) => jsonResponse(url === '/api/session' ? dispatcherSession : []))

    renderWithProviders(<AppRoutes />, { route: '/deliveries' })

    expect(await screen.findByRole('heading', { name: 'Deliveries' })).toBeInTheDocument()
  })

  it('returns to sign-in when the dispatcher signs out', async () => {
    let signedIn = true
    respondWith((url, method) => {
      if (url === '/api/session' && method === 'DELETE') {
        signedIn = false
        return noContentResponse()
      }
      if (url === '/api/session') {
        return signedIn
          ? jsonResponse(dispatcherSession)
          : problemResponse('authentication-required', 401)
      }
      return signedIn ? jsonResponse([]) : problemResponse('authentication-required', 401)
    })
    renderWithProviders(<AppRoutes />, { route: '/deliveries' })
    await screen.findByRole('heading', { name: 'Deliveries' })

    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Deliveries' })).not.toBeInTheDocument()
  })

  it('sends a signed-in courier to the courier workspace', async () => {
    respondWith((url) =>
      jsonResponse(url === '/api/session' ? courierSession : offDutyCourier),
    )

    renderWithProviders(<AppRoutes />, { route: '/' })

    expect(await screen.findByRole('heading', { name: 'Courier workspace' })).toBeInTheDocument()
  })
})
