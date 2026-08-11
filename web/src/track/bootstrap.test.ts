import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import BOOTSTRAP from '../../../server/src/main/resources/tracking/bootstrap.js?raw'
import { jsonResponse, noContentResponse, problemResponse, urlOf } from '../testing/support'

/**
 * The bootstrap script, read from the single copy the server inlines into /track. Importing it as a
 * module would need a second copy under web/src, and a second copy of the one file that must never
 * be wrong is exactly the thing worth avoiding.
 *
 * These tests run under jsdom, which is not a browser: it has no address bar, no real history stack
 * and no network. What it does have is a real `location`, a real `history.replaceState` and a real
 * DOM, so it genuinely executes the fragment read, the exchange and the replaceState — which is what
 * these assertions are about. DG-027's Playwright journeys are what exercise a real browser.
 */
const TOKEN = 'bm8tbGluay13YXMtZXZlci1kZXJpdmVkLWZyb20tdGg'

const UNAVAILABLE = 'This tracking link is no longer available. Contact the delivery team that shared it.'

/**
 * Runs the script against a document holding the two elements /track gives it.
 *
 * A real script element would be more faithful, but jsdom evaluates one in its own VM realm, where
 * the stubbed fetch does not exist. The Function constructor runs it in this realm instead. The rule
 * below guards against evaluating untrusted input; this input is a file in this repository that the
 * server inlines verbatim, and executing exactly it is the point of the test.
 */
function openTrackPageAt(hash: string) {
  document.body.innerHTML =
    '<p id="tracking-status">Opening your tracking link…</p><p id="tracking-content"></p>'
  window.history.replaceState(null, '', `/track${hash}`)
  // oxlint-disable-next-line typescript/no-implied-eval
  new Function(BOOTSTRAP)()
}

function status(): string {
  return document.getElementById('tracking-status')?.textContent ?? ''
}

function content(): string {
  return document.getElementById('tracking-content')?.textContent ?? ''
}

function fetchCalls() {
  return vi.mocked(fetch).mock.calls
}

/** Lets the promise chain inside the script run to completion before assertions. */
async function settle() {
  await vi.waitFor(() => expect(status()).not.toBe('Opening your tracking link…'))
}

describe('the /track bootstrap', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
    document.cookie = 'XSRF-TOKEN=csrf-cookie-value'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    window.history.replaceState(null, '', '/')
  })

  it('exchanges the fragment token and then shows the authorized delivery', async () => {
    vi.mocked(fetch).mockImplementation((input) =>
      Promise.resolve(
        urlOf(input) === '/api/tracking-session'
          ? noContentResponse()
          : jsonResponse({ deliveryReference: 'DG-0042' }),
      ),
    )

    openTrackPageAt(`#t=${TOKEN}`)
    await vi.waitFor(() => expect(content()).toContain('DG-0042'))
  })

  it('sends the token in the request body and never in a URL', async () => {
    vi.mocked(fetch).mockResolvedValue(noContentResponse())

    openTrackPageAt(`#t=${TOKEN}`)
    await vi.waitFor(() => expect(fetchCalls()).toHaveLength(2))

    const [exchangeInput, exchangeInit] = fetchCalls()[0]
    expect(urlOf(exchangeInput)).toBe('/api/tracking-session')
    expect(urlOf(exchangeInput)).not.toContain(TOKEN)
    expect(exchangeInit?.body).toBe(JSON.stringify({ token: TOKEN }))
  })

  it('posts the token exactly once', async () => {
    vi.mocked(fetch).mockResolvedValue(noContentResponse())

    openTrackPageAt(`#t=${TOKEN}`)
    await vi.waitFor(() => expect(fetchCalls()).toHaveLength(2))

    const exchanges = fetchCalls().filter(([input]) => urlOf(input) === '/api/tracking-session')
    expect(exchanges).toHaveLength(1)
  })

  it('removes the token from the URL as soon as the exchange succeeds', async () => {
    vi.mocked(fetch).mockResolvedValue(noContentResponse())

    openTrackPageAt(`#t=${TOKEN}`)
    await vi.waitFor(() => expect(window.location.hash).toBe(''))
    expect(window.location.href).not.toContain(TOKEN)
  })

  /** The failure path is the one that gets forgotten, and it leaves the token in history if it is. */
  it('removes the token from the URL when the exchange is refused', async () => {
    vi.mocked(fetch).mockResolvedValue(problemResponse('tracking-link-unavailable', 404))

    openTrackPageAt(`#t=${TOKEN}`)
    await settle()

    expect(window.location.hash).toBe('')
    expect(status()).toBe(UNAVAILABLE)
  })

  it('removes the token from the URL when the exchange cannot be sent at all', async () => {
    vi.mocked(fetch).mockRejectedValue(new TypeError('Failed to fetch'))

    openTrackPageAt(`#t=${TOKEN}`)
    await settle()

    expect(window.location.hash).toBe('')
    expect(status()).toContain('Could not reach the delivery service')
  })

  it('replaces the current history entry rather than pushing a token-free one on top', async () => {
    vi.mocked(fetch).mockResolvedValue(noContentResponse())
    const pushState = vi.spyOn(window.history, 'pushState')

    openTrackPageAt(`#t=${TOKEN}`)
    await vi.waitFor(() => expect(window.location.hash).toBe(''))

    expect(pushState).not.toHaveBeenCalled()
  })

  it('never sends the token again once the grant cookie exists', async () => {
    vi.mocked(fetch).mockImplementation((input) =>
      Promise.resolve(
        urlOf(input) === '/api/tracking-session'
          ? noContentResponse()
          : jsonResponse({ deliveryReference: 'DG-0042' }),
      ),
    )

    openTrackPageAt(`#t=${TOKEN}`)
    await vi.waitFor(() => expect(content()).toContain('DG-0042'))

    const laterCalls = fetchCalls().slice(1)
    expect(laterCalls).not.toHaveLength(0)
    for (const [input, init] of laterCalls) {
      expect(urlOf(input)).not.toContain(TOKEN)
      expect(JSON.stringify(init ?? {})).not.toContain(TOKEN)
    }
  })

  it('echoes the CSRF cookie as a header, the way every other command in this app does', async () => {
    vi.mocked(fetch).mockResolvedValue(noContentResponse())

    openTrackPageAt(`#t=${TOKEN}`)
    await vi.waitFor(() => expect(fetchCalls()).toHaveLength(2))

    const headers = fetchCalls()[0][1]?.headers as Record<string, string>
    expect(headers['X-XSRF-TOKEN']).toBe('csrf-cookie-value')
  })

  it.each([
    ['no fragment at all', ''],
    ['an empty token', '#t='],
    ['a token that is too short', '#t=tooshort'],
    ['a token carrying characters base64url does not use', `#t=${'+'.repeat(43)}`],
    ['a fragment that is not a token', '#hello'],
    ['a second parameter smuggled in', `#t=${TOKEN}&next=/admin`],
  ])('refuses to send %s anywhere', async (_description, hash) => {
    vi.mocked(fetch).mockResolvedValue(noContentResponse())

    openTrackPageAt(hash)

    expect(fetchCalls()).toHaveLength(0)
    expect(status()).toBe(UNAVAILABLE)
    expect(window.location.hash).toBe('')
  })

  it('shows the same unavailable wording the server uses, so the two cannot drift apart', async () => {
    vi.mocked(fetch).mockResolvedValue(problemResponse('tracking-link-unavailable', 404))

    openTrackPageAt(`#t=${TOKEN}`)
    await settle()

    expect(status()).toBe(UNAVAILABLE)
    expect(content()).toBe('')
  })
})
