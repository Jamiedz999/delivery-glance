import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SignInPage } from './SignInPage'
import { COURIER_ACCOUNT, DISPATCHER_ACCOUNT } from '../demoAccounts'
import {
  jsonResponse,
  noContentResponse,
  problemResponse,
  renderWithProviders,
  requestBodyStringOf,
  respondWith,
  urlOf,
} from '../testing/support'

const CANNOT_SUPPLY = /this deployment does not publish sign-in credentials/i

function systemStatus(demoAccountsUnchanged: boolean): Response {
  return jsonResponse({
    application: 'delivery-glance',
    status: 'ok',
    proofCaptureEnabled: false,
    etaEnabled: false,
    demoAccountsUnchanged,
  })
}

/** Signed out, with the demo-accounts probe answering however the case needs. */
function signedOut(system: () => Response) {
  respondWith((url) => {
    if (url === '/api/session') {
      return problemResponse('authentication-required', 401)
    }
    if (url === '/api/system') {
      return system()
    }
    return noContentResponse()
  })
}

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
    signedOut(() => systemStatus(true))
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
    respondWith((url) => {
      if (url === '/api/session') {
        return problemResponse('authentication-required', 401)
      }
      if (url === '/api/system') {
        return systemStatus(true)
      }
      return problemResponse('invalid-credentials', 401)
    })
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

  it('publishes the two demo accounts when the probe confirms they are unchanged', async () => {
    signedOut(() => systemStatus(true))
    renderWithProviders(<SignInPage />)

    expect(await screen.findByRole('button', { name: 'Use the Dispatcher account' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Use the Courier account' })).toBeInTheDocument()
    expect(screen.getByText(DISPATCHER_ACCOUNT.email)).toBeInTheDocument()
    expect(screen.getByText(DISPATCHER_ACCOUNT.password)).toBeInTheDocument()
    expect(screen.queryByText(CANNOT_SUPPLY)).not.toBeInTheDocument()
  })

  it('says it cannot supply credentials when the accounts were changed', async () => {
    signedOut(() => systemStatus(false))
    renderWithProviders(<SignInPage />)

    expect(await screen.findByText(CANNOT_SUPPLY)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Use the Dispatcher account' })).not.toBeInTheDocument()
  })

  it('says the same when the probe fails rather than guessing the accounts changed', async () => {
    signedOut(() => jsonResponse({ error: 'boom' }, 500))
    renderWithProviders(<SignInPage />)

    expect(await screen.findByText(CANNOT_SUPPLY)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Use the Dispatcher account' })).not.toBeInTheDocument()
  })

  it('renders neither the accounts nor the sentence while the probe is loading', async () => {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = urlOf(input)
      if (url === '/api/session') {
        return Promise.resolve(problemResponse('authentication-required', 401))
      }
      if (url === '/api/system') {
        return new Promise(() => {})
      }
      return Promise.resolve(noContentResponse())
    })
    renderWithProviders(<SignInPage />)

    await screen.findByRole('button', { name: 'Sign in' })
    expect(screen.queryByRole('button', { name: 'Use the Dispatcher account' })).not.toBeInTheDocument()
    expect(screen.queryByText(CANNOT_SUPPLY)).not.toBeInTheDocument()
  })

  it('fills the form from the account a button offers', async () => {
    signedOut(() => systemStatus(true))
    renderWithProviders(<SignInPage />)

    await userEvent.click(await screen.findByRole('button', { name: 'Use the Courier account' }))

    expect(screen.getByLabelText('Email')).toHaveValue(COURIER_ACCOUNT.email)
    expect(screen.getByLabelText('Password')).toHaveValue(COURIER_ACCOUNT.password)
  })
})
