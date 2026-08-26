import { useState } from 'react'
import type { FormEvent } from 'react'
import { Navigate } from 'react-router'
import { useSession, useSignIn } from '../api/queries'
import { ApiError } from '../api/http'
import { BrandMark } from '../components/BrandMark'

export function SignInPage() {
  const { data: session, isPending: isSessionPending } = useSession()
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

  return (
    <section className="auth">
      <div className="auth-brand">
        <BrandMark />
        <span className="app-wordmark">Delivery Glance</span>
      </div>
      <h1>Sign in</h1>
      <p className="auth-intro">
        Delivery Glance is used by the pre-provisioned accounts of one delivery team.
      </p>

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
    </section>
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
