/**
 * The Tracking Connection: the refresh stream, and the rule that it is never believed.
 *
 * What arrives here is "something about your delivery changed, and this is the version it changed
 * to". Nothing else is on the wire, so there is nothing a page could render from it even if it
 * wanted to: a hint's only effect is to fetch the snapshot, which is the same authorised read the
 * page already does on load. That is what makes a dropped, duplicated or late hint cost a redundant
 * fetch rather than a wrong page.
 *
 * The version is here so a page can skip work it has already done, not so it can notice a gap.
 * Reconnecting resets it, because the counter belongs to the connection: the server discards it when
 * the last page watching a Delivery goes away, and there is nothing to catch up on afterwards
 * anyway.
 */

import { useEffect, useState } from 'react'

/** What the page is told, and the whole of what a stream is allowed to tell it. */
export interface UpdateHandlers {
  /** Connected. Anything at all may have changed while it was not, so this is a refetch too. */
  onConnected(): void
  /** Something changed. The number orders hints against each other and describes nothing. */
  onChanged(version: number): void
  /**
   * The connection ended.
   *
   * @param retrying whether the browser will open a new one by itself. It will after a network
   * drop, and it will not when the server refused the request, which is what tells "we are
   * momentarily offline" apart from "this page is not getting updates any more".
   */
  onDropped(retrying: boolean): void
}

/** Opens a stream and returns the function that closes it. */
export type OpenUpdates = (handlers: UpdateHandlers) => () => void

export type TrackingConnection = 'connecting' | 'live' | 'reconnecting' | 'off'

export const UPDATES_PATH = '/api/tracking/events'

const SNAPSHOT_CHANGED = 'snapshot-changed'

/**
 * The real stream. Same-origin, so the grant cookie is sent exactly as it is for the snapshot, and
 * no token appears in the URL.
 *
 * A deployment without `EventSource` is a supported one rather than a failure: the page keeps
 * everything it renders and simply stops updating on its own, which is the same place a permanently
 * refused stream leaves it.
 */
export const openUpdates: OpenUpdates = (handlers) => {
  if (typeof EventSource === 'undefined') {
    handlers.onDropped(false)
    return () => {}
  }

  const source = new EventSource(UPDATES_PATH)
  source.addEventListener('open', () => handlers.onConnected())
  source.addEventListener(SNAPSHOT_CHANGED, (event) => handlers.onChanged(versionOf(event.data)))
  // EventSource reports "the connection failed" and "the server refused me" through the same
  // event; readyState is the only thing that separates them, and the page says different words
  // for each.
  source.addEventListener('error', () => handlers.onDropped(source.readyState !== EventSource.CLOSED))
  return () => source.close()
}

/**
 * A hint that cannot be read is still a hint. Returning a number that compares false against every
 * version already seen makes an unreadable frame cause the refetch it was asking for, rather than
 * being quietly dropped because its envelope was malformed.
 */
function versionOf(data: string): number {
  try {
    const parsed: unknown = JSON.parse(data)
    if (typeof parsed === 'object' && parsed !== null && 'version' in parsed) {
      const version = (parsed as { version: unknown }).version
      if (typeof version === 'number') {
        return version
      }
    }
  } catch {
    // Falls through to the same answer as a well-formed frame nobody understands.
  }
  return Number.NaN
}

/**
 * Keeps a stream open for as long as the page is mounted and calls `refresh` whenever the snapshot
 * it is showing may be out of date.
 *
 * @returns what to tell the reader about the connection. It is deliberately a separate answer from
 * Location Freshness: a page can be perfectly connected and showing a position nobody has updated
 * for two minutes, and conflating the two would let one of those hide the other.
 */
export function useSnapshotUpdates(refresh: () => void, open: OpenUpdates = openUpdates): TrackingConnection {
  const [connection, setConnection] = useState<TrackingConnection>('connecting')

  useEffect(() => {
    // Per connection, and reset with it: the server's counter starts again whenever a Delivery
    // stops being watched, so a version remembered across connections could silence a real hint.
    let seen = Number.NEGATIVE_INFINITY

    return open({
      onConnected: () => {
        seen = Number.NEGATIVE_INFINITY
        setConnection('live')
        refresh()
      },
      onChanged: (version) => {
        // Not `version > seen`: an unreadable version is NaN, and NaN fails both comparisons. This
        // way round it refetches, which is the safe answer to a hint nobody could read.
        if (!(version <= seen)) {
          seen = version
          refresh()
        }
      },
      onDropped: (retrying) => setConnection(retrying ? 'reconnecting' : 'off'),
    })
  }, [open, refresh])

  return connection
}
