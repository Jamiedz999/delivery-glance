import { useCallback, useEffect, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import type { Courier, LocationReportResult, StartedSession } from '../api/courier'
import { reportLocation, startLocationSharing, stopLocationSharing } from '../api/courier'
import { ApiError } from '../api/http'
import { queryKeys } from '../api/queries'

/** The four honest states a Courier is shown; none of them claims background tracking. */
export type SharingStatus = 'OFF' | 'STARTING' | 'REPORTING' | 'INTERRUPTED'

export const SHARING_STATUS_LABELS: Record<SharingStatus, string> = {
  OFF: 'Sharing off',
  STARTING: 'Starting — waiting for permission and a first position',
  REPORTING: 'Sharing your position',
  INTERRUPTED: 'Sharing interrupted',
}

/** A target, not a timer guarantee: whatever the browser has when it fires is what goes. */
const REPORT_INTERVAL_MS = 10_000

interface PendingFix {
  longitude: number
  latitude: number
  accuracyMetres: number
  recordedAt: string
}

export interface LocationSharing {
  status: SharingStatus
  notice: string | null
  busy: boolean
  start: () => Promise<void>
  stop: (endedBecause?: string | null) => Promise<void>
}

/**
 * Foreground Location Sharing for the open Courier page.
 *
 * The reporting secret lives in this hook and nowhere else — not in storage, not in a URL — so
 * closing or reloading the page really does end the session, exactly as the product promises.
 *
 * A hidden page stops collecting rather than collecting quietly: the watch is cleared, so the
 * browser is not asked for positions the product has no right to. At most one reading is ever
 * pending, so a page coming back sends the newest position it holds and never a trail.
 */
export function useLocationSharing(): LocationSharing {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<SharingStatus>('OFF')
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const session = useRef<StartedSession | null>(null)
  const pending = useRef<PendingFix | null>(null)
  const watch = useRef<number | null>(null)
  const cadence = useRef<ReturnType<typeof setInterval> | null>(null)
  const sending = useRef(false)
  const attempted = useRef(false)

  const stopWatching = useCallback(() => {
    if (watch.current !== null) {
      navigator.geolocation.clearWatch(watch.current)
      watch.current = null
    }
  }, [])

  const teardown = useCallback(() => {
    stopWatching()
    if (cadence.current !== null) {
      clearInterval(cadence.current)
      cadence.current = null
    }
    session.current = null
    pending.current = null
    attempted.current = false
  }, [stopWatching])

  const flush = useCallback(async () => {
    const current = session.current
    const fix = pending.current
    // A hidden page reports nothing, and a dropped fix is not queued: the next one replaces it.
    if (current === null || fix === null || sending.current || document.hidden) {
      return
    }
    sending.current = true
    // Counted as attempted rather than as sent, so a failed first report falls back to the ten
    // second cadence instead of retrying on every position the device produces.
    attempted.current = true
    pending.current = null
    try {
      const result = await reportLocation({
        generation: current.generation,
        reportingSecret: current.reportingSecret,
        ...fix,
      })
      queryClient.setQueryData<Courier>(queryKeys.courier, (previous) =>
        previous === undefined ? previous : { ...previous, location: result.location },
      )
      setStatus('REPORTING')
      setNotice(noticeFor(result))
    } catch (error) {
      if (error instanceof ApiError && error.code === 'location-sharing-ended') {
        teardown()
        setStatus('OFF')
        setNotice('The server ended this sharing session. Start sharing again to report a position.')
      } else {
        setStatus('INTERRUPTED')
        setNotice('That position could not be sent. The stored one keeps ageing until a report gets through.')
      }
    } finally {
      sending.current = false
    }
  }, [queryClient, teardown])

  const stop = useCallback(
    async (endedBecause: string | null = null) => {
      teardown()
      setStatus('OFF')
      setNotice(endedBecause)
      setBusy(true)
      try {
        // Sent even when this page held no session, so a session left behind by an earlier page
        // load can still be ended from here.
        await stopLocationSharing()
      } catch {
        setNotice('Sharing stopped here, but the server could not be told. It forgets the position within two minutes.')
      } finally {
        setBusy(false)
        await queryClient.invalidateQueries({ queryKey: queryKeys.courier })
      }
    },
    [queryClient, teardown],
  )

  /**
   * Asking the browser for positions is what the Courier consented to, so it happens only after
   * Start and only while the page is in front of them. A hidden page stops asking rather than
   * collecting readings it has no right to and quietly discarding them.
   */
  const startWatching = useCallback(() => {
    if (watch.current !== null) {
      return
    }
    watch.current = navigator.geolocation.watchPosition(
      (position) => {
        pending.current = {
          longitude: position.coords.longitude,
          latitude: position.coords.latitude,
          accuracyMetres: position.coords.accuracy,
          recordedAt: new Date(position.timestamp).toISOString(),
        }
        if (!attempted.current) {
          void flush()
        }
      },
      (error) => {
        if (error.code === error.PERMISSION_DENIED) {
          void stop('Location permission was refused, so sharing stopped and the server forgot your position.')
          return
        }
        setStatus('INTERRUPTED')
        setNotice(
          error.code === error.TIMEOUT
            ? 'No position yet. Reporting resumes as soon as the device produces one.'
            : 'The device cannot produce a position right now.',
        )
      },
      { enableHighAccuracy: true, maximumAge: 0, timeout: 15_000 },
    )
  }, [flush, stop])

  const start = useCallback(async () => {
    if (navigator.geolocation === undefined) {
      setStatus('OFF')
      setNotice('This browser cannot report a position.')
      return
    }

    setBusy(true)
    setStatus('STARTING')
    setNotice(null)
    try {
      session.current = await startLocationSharing()
    } catch {
      setStatus('OFF')
      setNotice('Location Sharing could not be started. Try again.')
      setBusy(false)
      return
    }

    attempted.current = false
    startWatching()
    cadence.current = setInterval(() => void flush(), REPORT_INTERVAL_MS)
    setBusy(false)
    await queryClient.invalidateQueries({ queryKey: queryKeys.courier })
  }, [flush, queryClient, startWatching])

  useEffect(() => {
    const onVisibilityChange = () => {
      if (session.current === null) {
        return
      }
      if (document.hidden) {
        stopWatching()
        setStatus('INTERRUPTED')
        setNotice('This page is in the background, so nothing is being collected or reported.')
        return
      }
      startWatching()
      setStatus(attempted.current ? 'REPORTING' : 'STARTING')
      setNotice(null)
      void flush()
    }

    document.addEventListener('visibilitychange', onVisibilityChange)
    return () => document.removeEventListener('visibilitychange', onVisibilityChange)
  }, [flush, startWatching, stopWatching])

  // Leaving the page ends foreground reporting. The server is not told, because a close signal is
  // not something a browser can be trusted to deliver; the position ages out instead.
  useEffect(() => teardown, [teardown])

  return { status, notice, busy, start, stop }
}

function noticeFor(result: LocationReportResult): string | null {
  switch (result.outcome) {
    case 'ACCEPTED':
      return null
    case 'REJECTED_LOW_ACCURACY':
      return 'That reading was coarser than 100 metres, so your position was left where it was.'
    case 'REJECTED_STALE':
      return 'That reading was already more than two minutes old, so it was discarded.'
    case 'REJECTED_FUTURE_DATED':
      return 'That reading was dated in the future, so it was discarded. Check this device’s clock.'
    case 'REJECTED_NOT_NEWER':
      return 'That reading was not newer than the one already stored, so nothing changed.'
  }
}
