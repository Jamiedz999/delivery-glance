import { afterEach, describe, expect, it, vi } from 'vitest'
import { UPDATES_PATH, openUpdates } from './updates'

/**
 * The one place the real `EventSource` is used, tested against a stand-in for it.
 *
 * jsdom does not implement EventSource, so this is not a shortcut around a testable module — it is
 * the only way to reach this code at all. What it pins is the handful of facts the rest of the page
 * assumes and could not discover for itself: the URL that gets opened, that the grant travels as a
 * cookie rather than in that URL, and which of the two things `error` means each time it fires.
 */
class FakeEventSource {
  static readonly CONNECTING = 0
  static readonly OPEN = 1
  static readonly CLOSED = 2

  static opened: FakeEventSource[] = []

  readyState: number = FakeEventSource.CONNECTING
  closed = false
  readonly url: string

  private readonly listeners = new Map<string, ((event: MessageEvent) => void)[]>()

  constructor(url: string) {
    this.url = url
    FakeEventSource.opened.push(this)
  }

  addEventListener(type: string, listener: (event: MessageEvent) => void) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener])
  }

  close() {
    this.closed = true
    this.readyState = FakeEventSource.CLOSED
  }

  emit(type: string, data = '') {
    for (const listener of this.listeners.get(type) ?? []) {
      listener(new MessageEvent(type, { data }))
    }
  }
}

function handlerSpies() {
  return {
    onConnected: vi.fn(),
    onChanged: vi.fn(),
    onDropped: vi.fn(),
  }
}

function openWithFake() {
  FakeEventSource.opened = []
  vi.stubGlobal('EventSource', FakeEventSource)
  const handlers = handlerSpies()
  const close = openUpdates(handlers)
  return { handlers, close, source: FakeEventSource.opened[0] }
}

describe('the Recipient refresh stream', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('opens the same-origin events route and puts nothing in the URL', () => {
    const { source } = openWithFake()

    expect(source.url).toBe(UPDATES_PATH)
    // The grant is a cookie the browser attaches to a same-origin request. A URL that carried it
    // would put it in every log and referrer the page ever produces.
    expect(source.url).not.toContain('?')
  })

  it('reports a connection, and the version of each hint it is sent', () => {
    const { handlers, source } = openWithFake()

    source.readyState = FakeEventSource.OPEN
    source.emit('open')
    source.emit('snapshot-changed', '{"version":7}')

    expect(handlers.onConnected).toHaveBeenCalledOnce()
    expect(handlers.onChanged).toHaveBeenCalledWith(7)
  })

  /**
   * The version is the only field, and a frame carrying anything else is a frame this page does not
   * understand. It reports a version that compares false against every version already seen, so the
   * hint still causes the refetch it was asking for.
   */
  it('reports an unreadable hint rather than swallowing it', () => {
    const { handlers, source } = openWithFake()

    source.emit('snapshot-changed', 'not json at all')
    source.emit('snapshot-changed', '{"state":"DELIVERED"}')

    expect(handlers.onChanged).toHaveBeenCalledTimes(2)
    expect(handlers.onChanged.mock.calls.every(([version]) => Number.isNaN(version))).toBe(true)
  })

  it('tells a dropped connection apart from a refused one', () => {
    const { handlers, source } = openWithFake()

    // The browser will open another one by itself.
    source.readyState = FakeEventSource.CONNECTING
    source.emit('error')
    expect(handlers.onDropped).toHaveBeenLastCalledWith(true)

    // The server refused the request, so it has given up.
    source.readyState = FakeEventSource.CLOSED
    source.emit('error')
    expect(handlers.onDropped).toHaveBeenLastCalledWith(false)
  })

  it('closes the connection when the page is done with it', () => {
    const { close, source } = openWithFake()

    close()

    expect(source.closed).toBe(true)
  })

  /**
   * A browser without EventSource is a supported deployment rather than a crash: the page keeps
   * everything it renders and stops updating on its own, which is where a refused stream leaves it
   * too.
   */
  it('reports no updates rather than failing where EventSource does not exist', () => {
    vi.stubGlobal('EventSource', undefined)
    const handlers = handlerSpies()

    expect(() => openUpdates(handlers)()).not.toThrow()
    expect(handlers.onDropped).toHaveBeenCalledWith(false)
  })
})
