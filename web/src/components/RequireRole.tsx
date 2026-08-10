import type { ReactNode } from 'react'
import { Navigate } from 'react-router'
import type { InternalAccountRole } from '../api/session'
import { useSession } from '../api/queries'

const ROLE_LABELS: Record<InternalAccountRole, string> = {
  DISPATCHER: 'Dispatcher',
  COURIER: 'Courier',
}

interface RequireRoleProps {
  role: InternalAccountRole
  children: ReactNode
}

/**
 * Keeps a route out of the wrong workspace. The API enforces the same rule, so this only decides
 * what to render, never whether the data may be read.
 */
export function RequireRole({ role, children }: RequireRoleProps) {
  const { data: session, isPending, isError } = useSession()

  if (isPending) {
    return <p role="status">Checking your session…</p>
  }

  if (isError) {
    return <p role="alert">Could not check your session. Reload the page to try again.</p>
  }

  if (session === null) {
    return <Navigate to="/sign-in" replace />
  }

  if (session.role !== role) {
    return (
      <p role="alert">
        This page is only available to a {ROLE_LABELS[role]}. You are signed in as a{' '}
        {ROLE_LABELS[session.role]}.
      </p>
    )
  }

  return <>{children}</>
}
