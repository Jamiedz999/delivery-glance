import { Link, Outlet } from 'react-router'
import { useSession, useSignOut } from '../api/queries'

export function AppLayout() {
  const { data: session } = useSession()
  const signOut = useSignOut()

  return (
    <>
      <header className="app-header">
        <Link to="/" className="app-title">
          Delivery Glance
        </Link>
        {session != null && (
          <div className="app-identity">
            <span>
              {session.displayName} · {session.role === 'DISPATCHER' ? 'Dispatcher' : 'Courier'}
            </span>
            <button
              type="button"
              onClick={() => signOut.mutate()}
              disabled={signOut.isPending}
              aria-busy={signOut.isPending}
            >
              Sign out
            </button>
          </div>
        )}
      </header>
      <main>
        <Outlet />
      </main>
    </>
  )
}
