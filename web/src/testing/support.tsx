import type { ReactNode } from 'react'
import { vi } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router'
import { render } from '@testing-library/react'

/** Renders a page the way the app does, minus the retries that would slow failing tests down. */
export function renderWithProviders(ui: ReactNode, { route = '/' }: { route?: string } = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
    </QueryClientProvider>,
  )
}

export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

export function problemResponse(
  code: string,
  status: number,
  extra: Record<string, unknown> = {},
): Response {
  return new Response(JSON.stringify({ code, status, ...extra }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

export function noContentResponse(): Response {
  return new Response(null, { status: 204 })
}

export function requestBodyOf(call: [RequestInfo | URL, RequestInit | undefined]): unknown {
  return JSON.parse(String(call[1]?.body))
}

/**
 * A response body can only be read once, and a page may fetch the same endpoint more than once, so
 * every call gets a freshly built response.
 */
export function respondWith(build: (url: string, method: string) => Response) {
  vi.mocked(fetch).mockImplementation((input, init) =>
    Promise.resolve(build(String(input), (init?.method ?? 'GET').toUpperCase())),
  )
}
