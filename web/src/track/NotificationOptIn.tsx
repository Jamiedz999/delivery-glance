import { type FormEvent, useEffect, useState } from 'react'
import {
  type NotificationChannel,
  type OptInState,
  fetchOptInState,
  revoke,
  subscribe,
} from './notifications'

/**
 * The opt-in a Recipient volunteers from the tracking page: a channel and an address to be told when
 * the delivery changes state, off-band, when this page is closed. Nothing is stored until they enter
 * it, which is the whole privacy stance — the team never holds a contact the Recipient did not give.
 *
 * It renders nothing at all when the deployment cannot notify (no queue configured), so a page that
 * could never deliver an update never offers to. That decision is the server's, read once on mount.
 */
export function NotificationOptIn() {
  const [state, setState] = useState<OptInState | null>(null)

  useEffect(() => {
    let active = true
    void fetchOptInState().then((loaded) => {
      if (active) {
        setState(loaded)
      }
    })
    return () => {
      active = false
    }
  }, [])

  if (state === null || !state.available) {
    return null
  }

  if (state.subscription !== null && state.subscription.active) {
    return (
      <ActiveSubscription
        channel={state.subscription.channel}
        target={state.subscription.target}
        onTurnedOff={() => setState({ available: true, subscription: null })}
      />
    )
  }

  return <OptInForm onSubscribed={(subscription) => setState({ available: true, subscription })} onOff={() => setState({ available: false, subscription: null })} />
}

function ActiveSubscription({
  channel,
  target,
  onTurnedOff,
}: {
  channel: NotificationChannel
  target: string
  onTurnedOff: () => void
}) {
  const [working, setWorking] = useState(false)

  async function turnOff() {
    setWorking(true)
    const done = await revoke()
    setWorking(false)
    if (done) {
      onTurnedOff()
    }
  }

  return (
    <section className="card opt-in" aria-labelledby="opt-in-heading">
      <h3 id="opt-in-heading">Delivery updates</h3>
      <p>
        We’ll {channel === 'EMAIL' ? 'email' : 'text'} you at <strong>{target}</strong> when this delivery changes.
      </p>
      <button type="button" onClick={() => void turnOff()} disabled={working}>
        Turn off updates
      </button>
    </section>
  )
}

function OptInForm({
  onSubscribed,
  onOff,
}: {
  onSubscribed: (subscription: { channel: NotificationChannel; target: string; active: boolean }) => void
  onOff: () => void
}) {
  const [channel, setChannel] = useState<NotificationChannel>('EMAIL')
  const [target, setTarget] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    const result = await subscribe(channel, target.trim())
    setSubmitting(false)
    switch (result.status) {
      case 'ok':
        onSubscribed(result.subscription)
        return
      case 'invalid':
        setError(channel === 'EMAIL' ? 'Enter a valid email address.' : 'Enter a phone number like +15551234567.')
        return
      case 'unavailable':
        // The feature went off underneath the page; stop offering it rather than retrying.
        onOff()
        return
      case 'error':
        setError('That couldn’t be saved just now. Try again.')
    }
  }

  return (
    <section className="card opt-in" aria-labelledby="opt-in-heading">
      <h3 id="opt-in-heading">Get delivery updates</h3>
      <p>Be notified when this delivery changes, even when this page is closed.</p>
      <form onSubmit={(event) => void onSubmit(event)} noValidate>
        <fieldset className="opt-in-channel">
          <legend>How should we reach you?</legend>
          <label>
            <input
              type="radio"
              name="opt-in-channel"
              checked={channel === 'EMAIL'}
              onChange={() => {
                setChannel('EMAIL')
                setError(null)
              }}
            />
            Email
          </label>
          <label>
            <input
              type="radio"
              name="opt-in-channel"
              checked={channel === 'SMS'}
              onChange={() => {
                setChannel('SMS')
                setError(null)
              }}
            />
            Text message
          </label>
        </fieldset>

        <label className="opt-in-target">
          {channel === 'EMAIL' ? 'Email address' : 'Phone number'}
          <input
            type={channel === 'EMAIL' ? 'email' : 'tel'}
            value={target}
            onChange={(event) => setTarget(event.target.value)}
            autoComplete={channel === 'EMAIL' ? 'email' : 'tel'}
            required
          />
        </label>

        {error !== null && (
          <p className="opt-in-error" role="alert">
            {error}
          </p>
        )}

        <button type="submit" disabled={submitting || target.trim() === ''}>
          Notify me
        </button>
      </form>
    </section>
  )
}
