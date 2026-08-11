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

/**
 * fetch's first argument may be a string, a URL or a Request, and each keeps its URL somewhere
 * different. Stringifying the union blindly turns a Request into "[object Request]", which then
 * matches no path and fails the assertion for the wrong reason.
 */
export function urlOf(input: RequestInfo | URL): string {
  if (typeof input === 'string') {
    return input
  }
  return input instanceof URL ? input.href : input.url
}

/**
 * The request body of a call the app made, as the string it was sent as. A JSON body arrives as a
 * string and a form body as URLSearchParams, both of which have one honest string form; the rest of
 * the BodyInit union (Blob, FormData, streams) does not, and stringifying one would silently
 * compare "[object Object]" instead of failing.
 */
export function requestBodyStringOf(init: RequestInit | undefined): string {
  const body = init?.body
  if (typeof body === 'string') {
    return body
  }
  if (body instanceof URLSearchParams) {
    return body.toString()
  }
  throw new Error(`Expected a string or URLSearchParams request body, got ${typeof body}.`)
}

export function requestBodyOf(call: [RequestInfo | URL, RequestInit | undefined]): unknown {
  return JSON.parse(requestBodyStringOf(call[1]))
}

/**
 * A response body can only be read once, and a page may fetch the same endpoint more than once, so
 * every call gets a freshly built response.
 */
export function respondWith(build: (url: string, method: string) => Response) {
  vi.mocked(fetch).mockImplementation((input, init) =>
    Promise.resolve(build(urlOf(input), (init?.method ?? 'GET').toUpperCase())),
  )
}
