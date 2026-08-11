import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SignInPage } from './SignInPage'
import {
  jsonResponse,
  noContentResponse,
  problemResponse,
  renderWithProviders,
  requestBodyStringOf,
  respondWith,
} from '../testing/support'

describe('SignInPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
    document.cookie = 'XSRF-TOKEN=csrf-token-1'
  })

  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('signs in with the entered credentials and the CSRF header', async () => {
    respondWith((url) =>
      url === '/api/session' ? problemResponse('authentication-required', 401) : noContentResponse(),
    )
    renderWithProviders(<SignInPage />)
    await screen.findByRole('button', { name: 'Sign in' })

    await userEvent.type(screen.getByLabelText('Email'), 'dispatcher@delivery-glance.example')
    await userEvent.type(screen.getByLabelText('Password'), 'Dispatcher-Demo-2026!')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    await waitFor(() => {
      const call = vi.mocked(fetch).mock.calls.find(([url]) => url === '/api/session/login')
      expect(call).toBeDefined()
      expect(new Headers(call?.[1]?.headers).get('X-XSRF-TOKEN')).toBe('csrf-token-1')
      expect(requestBodyStringOf(call?.[1])).toBe(
        'email=dispatcher%40delivery-glance.example&password=Dispatcher-Demo-2026%21',
      )
    })
  })

  it('reports a rejected sign-in without saying which field was wrong', async () => {
    respondWith((url) =>
      url === '/api/session'
        ? problemResponse('authentication-required', 401)
        : problemResponse('invalid-credentials', 401),
    )
    renderWithProviders(<SignInPage />)
    await screen.findByRole('button', { name: 'Sign in' })

    await userEvent.type(screen.getByLabelText('Email'), 'dispatcher@delivery-glance.example')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The email and password do not match an enabled account.',
    )
  })

  it('shows a loading state while the session is being checked', () => {
    vi.mocked(fetch).mockReturnValue(new Promise(() => {}))

    renderWithProviders(<SignInPage />)

    expect(screen.getByRole('status')).toHaveTextContent(/checking your session/i)
  })

  it('leaves the form once a session exists', async () => {
    respondWith(() => jsonResponse({ displayName: 'Dana the Dispatcher', role: 'DISPATCHER' }))

    renderWithProviders(<SignInPage />)

    await waitFor(() => expect(screen.queryByLabelText('Email')).not.toBeInTheDocument())
  })
})
