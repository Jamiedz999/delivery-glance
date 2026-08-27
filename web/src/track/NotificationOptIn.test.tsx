import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { jsonResponse, noContentResponse, problemResponse, urlOf } from '../testing/support'
import { NotificationOptIn } from './NotificationOptIn'

function fetchCalls() {
  return vi.mocked(fetch).mock.calls
}

function callsTo(method: string) {
  return fetchCalls().filter(([, init]) => (init?.method ?? 'GET').toUpperCase() === method)
}

describe('NotificationOptIn', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
    document.cookie = 'XSRF-TOKEN=csrf-cookie-value'
  })

  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('renders nothing when the deployment cannot notify', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse({ available: false, subscription: null }))

    const { container } = render(<NotificationOptIn />)

    await waitFor(() => expect(fetchCalls()).toHaveLength(1))
    expect(container).toBeEmptyDOMElement()
    expect(screen.queryByRole('heading')).toBeNull()
  })

  it('offers a form, opts in with a valid email, and echoes the CSRF token', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(jsonResponse({ available: true, subscription: null }))
      .mockResolvedValueOnce(jsonResponse({ channel: 'EMAIL', target: 'r@example.com', active: true }))

    render(<NotificationOptIn />)
    await screen.findByRole('heading', { name: 'Get delivery updates' })

    await userEvent.type(screen.getByLabelText('Email address'), 'r@example.com')
    await userEvent.click(screen.getByRole('button', { name: 'Notify me' }))

    await screen.findByText(/We’ll email you at/)
    expect(screen.getByText('r@example.com')).toBeInTheDocument()

    const post = callsTo('POST')[0]
    expect(urlOf(post[0])).toBe('/api/tracking/notifications')
    expect((post[1]?.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('csrf-cookie-value')
    expect(JSON.parse(post[1]?.body as string)).toEqual({ channel: 'EMAIL', target: 'r@example.com' })
  })

  it('shows a rejected target inline without leaving the form', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(jsonResponse({ available: true, subscription: null }))
      .mockResolvedValueOnce(problemResponse('notification-invalid-target', 422))

    render(<NotificationOptIn />)
    await screen.findByRole('heading', { name: 'Get delivery updates' })

    await userEvent.type(screen.getByLabelText('Email address'), 'not-an-email')
    await userEvent.click(screen.getByRole('button', { name: 'Notify me' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Enter a valid email address.')
    expect(screen.getByRole('button', { name: 'Notify me' })).toBeInTheDocument()
  })

  it('shows an existing subscription and turns it off', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(
        jsonResponse({ available: true, subscription: { channel: 'SMS', target: '+15551234567', active: true } }),
      )
      .mockResolvedValueOnce(noContentResponse())

    render(<NotificationOptIn />)
    await screen.findByText(/We’ll text you at/)
    expect(screen.getByText('+15551234567')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Turn off updates' }))

    // Turned off returns to the opt-in form, and a DELETE was sent.
    await screen.findByRole('heading', { name: 'Get delivery updates' })
    expect(callsTo('DELETE')).toHaveLength(1)
  })
})
