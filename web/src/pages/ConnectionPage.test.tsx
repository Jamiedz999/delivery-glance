import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ConnectionPage } from './ConnectionPage'

function renderConnectionPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <ConnectionPage />
    </QueryClientProvider>,
  )
}

describe('ConnectionPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('shows a loading state before the API responds', () => {
    vi.mocked(fetch).mockReturnValue(new Promise(() => {}))

    renderConnectionPage()

    expect(screen.getByRole('status')).toHaveTextContent(/connecting/i)
  })

  it('shows the connected success state once the API responds', async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ application: 'delivery-glance', status: 'ok' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    renderConnectionPage()

    await waitFor(() =>
      expect(screen.getByRole('status')).toHaveTextContent('Frontend connected to Delivery Glance API'),
    )
  })

  it('shows an error state when the API request fails', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 500 }))

    renderConnectionPage()

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('Could not reach the Delivery Glance API.')
    })
  })
})
