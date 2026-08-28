import { useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { Navigate } from 'react-router'
import { useSession, useSignIn } from '../api/queries'
import { useSystemStatus } from '../api/useSystemStatus'
import { ApiError } from '../api/http'
import { BrandMark } from '../components/BrandMark'
import { DEMO_ACCOUNTS } from '../demoAccounts'
import type { DemoAccount } from '../demoAccounts'

const DEMO_SCRIPT_URL = 'https://github.com/Jamiedz999/delivery-glance/blob/main/docs/demo-script.md'

export function SignInPage() {
  const { data: session, isPending: isSessionPending } = useSession()
  const status = useSystemStatus()
  const signIn = useSignIn()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  if (isSessionPending) {
    return <p role="status">Checking your session…</p>
  }

  if (session != null) {
    return <Navigate to="/" replace />
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    signIn.mutate({ email, password })
  }

  function fillWith(account: DemoAccount) {
    setEmail(account.email)
    setPassword(account.password)
  }

  return (
    <section className="auth">
      <div className="auth-lead">
        <div className="auth-brand">
          <BrandMark />
          <h1>Delivery Glance</h1>
        </div>
        <p className="auth-tagline">
          Follow one Delivery from all three sides. It takes three browser windows, because it is three people
          — one window holds one session.
        </p>
        <ol className="auth-steps">
          <li>
            Sign in as the <strong>Dispatcher</strong> and open a Delivery.
          </li>
          <li>
            In a second window, sign in as the <strong>Courier</strong>, go on duty and start sharing.
          </li>
          <li>
            Back in the first window, copy the Tracking Link and open it in a third window — that is the{' '}
            <strong>Recipient</strong>, who has no account.
          </li>
        </ol>
        <p className="auth-script">
          The full walkthrough is{' '}
          <a href={DEMO_SCRIPT_URL} target="_blank" rel="noreferrer">
            the demo script
          </a>
          .
        </p>
        {demoAccounts(status, fillWith)}
      </div>

      <div className="auth-form">
        <h2>Sign in</h2>

        {signIn.isError && (
          <p role="alert" className="error">
            {messageFor(signIn.error)}
          </p>
        )}

        <form onSubmit={submit} noValidate>
          <div className="field">
            <label htmlFor="email">Email</label>
            <input
              id="email"
              name="email"
              type="email"
              autoComplete="username"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
          </div>

          <div className="field">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </div>

          <button
            type="submit"
            className="btn-primary"
            disabled={signIn.isPending}
            aria-busy={signIn.isPending}
          >
            {signIn.isPending ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
      </div>
    </section>
  )
}

/**
 * The two seeded accounts, shown only when the server confirms they are still the fictional ones.
 *
 * While the probe is loading, nothing is rendered here rather than the credentials being shown and
 * then taken away. They appear only on an explicit `true`. On `false` and on a failed probe alike,
 * the same sentence: the page cannot supply them — which is all it honestly knows. Deliberately not
 * `data?.demoAccountsUnchanged ?? false`, which the other probe callers use: here that would turn
 * "still loading" into "the accounts were changed", a claim the page has no basis to make yet.
 */
function demoAccounts(
  status: ReturnType<typeof useSystemStatus>,
  fillWith: (account: DemoAccount) => void,
): ReactNode {
  if (status.isPending) {
    return null
  }

  if (status.data?.demoAccountsUnchanged !== true) {
    return (
      <p className="auth-accounts-note">
        This deployment does not publish sign-in credentials — ask whoever deployed it.
      </p>
    )
  }

  return (
    <div className="auth-accounts">
      <h2>Sign in with</h2>
      <ul>
        {DEMO_ACCOUNTS.map((account) => (
          <li key={account.email} className="auth-account">
            <div className="auth-account-detail">
              <span className="auth-account-role">{account.role}</span>
              <span className="auth-account-cred">{account.email}</span>
              <span className="auth-account-cred">{account.password}</span>
            </div>
            <button type="button" className="btn-secondary" onClick={() => fillWith(account)}>
              Use the {account.role} account
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}

function messageFor(error: unknown): string {
  if (error instanceof ApiError && error.code === 'invalid-credentials') {
    return 'The email and password do not match an enabled account.'
  }
  if (error instanceof ApiError && error.code === 'csrf-token-invalid') {
    return 'Your page has been open too long. Reload it and sign in again.'
  }
  return 'Could not reach the Delivery Glance API. Try again in a moment.'
}
