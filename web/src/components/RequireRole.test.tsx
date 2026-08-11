import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router'
import { RequireRole } from './RequireRole'
import { jsonResponse, problemResponse, renderWithProviders, respondWith } from '../testing/support'

function renderGuardedPage() {
  renderWithProviders(
    <Routes>
      <Route
        path="/deliveries"
        element={
          <RequireRole role="DISPATCHER">
            <p>Dispatcher workspace</p>
          </RequireRole>
        }
      />
      <Route path="/sign-in" element={<p>Sign in</p>} />
    </Routes>,
    { route: '/deliveries' },
  )
}

describe('RequireRole', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('shows the page to the matching role', async () => {
    respondWith(() => jsonResponse({ displayName: 'Dana the Dispatcher', role: 'DISPATCHER' }))

    renderGuardedPage()

    expect(await screen.findByText('Dispatcher workspace')).toBeInTheDocument()
  })

  it('keeps a dispatcher route away from a signed-in courier', async () => {
    respondWith(() => jsonResponse({ displayName: 'Cory the Courier', role: 'COURIER' }))

    renderGuardedPage()

    expect(await screen.findByRole('alert')).toHaveTextContent('This page is only available to a Dispatcher.')
    expect(screen.queryByText('Dispatcher workspace')).not.toBeInTheDocument()
  })

  it('sends a signed-out visitor to sign-in', async () => {
    respondWith(() => problemResponse('authentication-required', 401))

    renderGuardedPage()

    await waitFor(() => expect(screen.getByText('Sign in')).toBeInTheDocument())
    expect(screen.queryByText('Dispatcher workspace')).not.toBeInTheDocument()
  })

  it('shows a loading state while the session is being checked', () => {
    vi.mocked(fetch).mockReturnValue(new Promise(() => {}))

    renderGuardedPage()

    expect(screen.getByRole('status')).toHaveTextContent(/checking your session/i)
  })
})
