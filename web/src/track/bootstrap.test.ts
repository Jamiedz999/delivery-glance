import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import BOOTSTRAP from '../../../server/src/main/resources/tracking/bootstrap.js?raw'
import { noContentResponse, problemResponse, urlOf } from '../testing/support'

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
  document.head.innerHTML = ''
  document.body.innerHTML =
    '<p id="tracking-status">Opening your tracking link…</p><div id="tracking-app"></div>'
  window.history.replaceState(null, '', `/track${hash}`)
  // oxlint-disable-next-line typescript/no-implied-eval
  new Function(BOOTSTRAP)()
}

function status(): string {
  return document.getElementById('tracking-status')?.textContent ?? ''
}

/** The application bundle, if the bootstrap has asked for it. */
function applicationScript(): HTMLScriptElement | null {
  return document.querySelector('script[src="/track-app.js"]')
}

function applicationStylesheet(): HTMLLinkElement | null {
  return document.querySelector('link[href="/track-app.css"]')
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

  it('exchanges the fragment token and only then asks for the Recipient application', async () => {
    vi.mocked(fetch).mockResolvedValue(noContentResponse())

    openTrackPageAt(`#t=${TOKEN}`)
    expect(applicationScript()).toBeNull()

    await vi.waitFor(() => expect(applicationScript()).not.toBeNull())
    expect(applicationScript()?.type).toBe('module')
    expect(applicationStylesheet()).not.toBeNull()
  })

  /**
   * The strongest form of "the map is never loaded before a successful bootstrap": there is no
   * application to load a map, because nothing has asked the browser for one.
   */
  it.each([
    ['the exchange is refused', () => vi.mocked(fetch).mockResolvedValue(problemResponse('x', 404))],
    [
      'the exchange cannot be sent',
      () => vi.mocked(fetch).mockRejectedValue(new TypeError('Failed to fetch')),
    ],
  ])('never asks for the application when %s', async (_description, arrange) => {
    arrange()

    openTrackPageAt(`#t=${TOKEN}`)
    await settle()

    expect(applicationScript()).toBeNull()
    expect(applicationStylesheet()).toBeNull()
  })

  /**
   * The reload path. This page removes the fragment as soon as it has spent it, so every visit
   * after the first arrives with none — and DG-025's only way to see a newer position is to
   * reload. It must therefore reach the application on the grant cookie alone, and it must do so
   * without an exchange, because there is no longer a token to exchange.
   */
  it('loads the application on the grant cookie alone when there is no fragment left', async () => {
    vi.mocked(fetch).mockResolvedValue(noContentResponse())

    openTrackPageAt('')

    await vi.waitFor(() => expect(applicationScript()).not.toBeNull())
    expect(fetchCalls()).toHaveLength(0)
  })

  /** A grant the browser holds is no use if the application it authorizes never arrives. */
  it('says the service is unreachable when the application bundle fails to load', async () => {
    vi.mocked(fetch).mockResolvedValue(noContentResponse())

    openTrackPageAt(`#t=${TOKEN}`)
    await vi.waitFor(() => expect(applicationScript()).not.toBeNull())
    applicationScript()?.dispatchEvent(new Event('error'))

    expect(status()).toContain('Could not reach the delivery service')
  })

  it('sends the token in the request body and never in a URL', async () => {
    vi.mocked(fetch).mockResolvedValue(noContentResponse())

    openTrackPageAt(`#t=${TOKEN}`)
    await vi.waitFor(() => expect(applicationScript()).not.toBeNull())

    const [exchangeInput, exchangeInit] = fetchCalls()[0]
    expect(urlOf(exchangeInput)).toBe('/api/tracking-session')
    expect(urlOf(exchangeInput)).not.toContain(TOKEN)
    expect(exchangeInit?.body).toBe(JSON.stringify({ token: TOKEN }))
  })

  it('posts the token exactly once', async () => {
    vi.mocked(fetch).mockResolvedValue(noContentResponse())

    openTrackPageAt(`#t=${TOKEN}`)
    await vi.waitFor(() => expect(applicationScript()).not.toBeNull())

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

  /**
   * The exchange is the last thing that ever carries the token. Everything the page loads after it
   * is named by a fixed path, so nothing downstream can be handed a capability to put in a URL.
   */
  it('never sends the token again once the grant cookie exists', async () => {
    vi.mocked(fetch).mockResolvedValue(noContentResponse())

    openTrackPageAt(`#t=${TOKEN}`)
    await vi.waitFor(() => expect(applicationScript()).not.toBeNull())

    expect(fetchCalls()).toHaveLength(1)
    expect(applicationScript()?.getAttribute('src')).toBe('/track-app.js')
    expect(applicationStylesheet()?.getAttribute('href')).toBe('/track-app.css')
    expect(document.documentElement.innerHTML).not.toContain(TOKEN)
  })

  it('echoes the CSRF cookie as a header, the way every other command in this app does', async () => {
    vi.mocked(fetch).mockResolvedValue(noContentResponse())

    openTrackPageAt(`#t=${TOKEN}`)
    await vi.waitFor(() => expect(applicationScript()).not.toBeNull())

    const headers = fetchCalls()[0][1]?.headers as Record<string, string>
    expect(headers['X-XSRF-TOKEN']).toBe('csrf-cookie-value')
  })

  it.each([
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
    // Nor is a grant this browser happens to hold used to answer a broken link with some other
    // Delivery, which would be a confusing kind of correct.
    expect(applicationScript()).toBeNull()
  })

  it('shows the same unavailable wording the server uses, so the two cannot drift apart', async () => {
    vi.mocked(fetch).mockResolvedValue(problemResponse('tracking-link-unavailable', 404))

    openTrackPageAt(`#t=${TOKEN}`)
    await settle()

    expect(status()).toBe(UNAVAILABLE)
    expect(applicationScript()).toBeNull()
  })

  it('announces a dead end as one, in the same words and the same way the application would', async () => {
    // The placeholder opens as role="status" because it opens carrying "Opening your tracking
    // link…", which is progress and should wait its turn. Neither message this can replace it with
    // is progress: both end the visit. The Recipient application says the identical sentence
    // through role="alert" when a snapshot read is refused, and one refusal announced two ways
    // depending on which script happened to be running is a difference nobody could justify.
    vi.mocked(fetch).mockResolvedValue(problemResponse('tracking-link-unavailable', 404))

    openTrackPageAt(`#t=${TOKEN}`)
    await settle()

    expect(document.getElementById('tracking-status')).toHaveAttribute('role', 'alert')
  })
})
