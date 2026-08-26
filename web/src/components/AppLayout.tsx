import { Link, NavLink, Outlet } from 'react-router'
import { useSession, useSignOut } from '../api/queries'
import { BrandMark } from './BrandMark'

export function AppLayout() {
  const { data: session } = useSession()
  const signOut = useSignOut()
  const isDispatcher = session?.role === 'DISPATCHER'

  return (
    <>
      <header className="app-header">
        <Link to="/" className="app-brand">
          <BrandMark />
          <span className="app-wordmark">Delivery Glance</span>
          {session != null && <span className="app-workspace">{isDispatcher ? 'Dispatch' : 'Courier'}</span>}
        </Link>
        {session != null && (
          <div className="app-identity">
            <span className="app-account">
              <span className="app-account-name">{session.displayName}</span>
              <span className="app-account-role">{isDispatcher ? 'Dispatcher' : 'Courier'}</span>
            </span>
            <button
              type="button"
              className="btn-ghost"
              onClick={() => signOut.mutate()}
              disabled={signOut.isPending}
              aria-busy={signOut.isPending}
            >
              Sign out
            </button>
          </div>
        )}
      </header>
      <div className="app-shell" data-nav={isDispatcher ? 'true' : 'false'}>
        {isDispatcher && (
          <nav className="app-nav" aria-label="Dispatcher">
            <p className="app-nav-group">Operations</p>
            <NavLink to="/deliveries" className="app-nav-link">
              Deliveries
            </NavLink>
          </nav>
        )}
        <main className="app-main">
          <div className="app-main-inner">
            <Outlet />
          </div>
        </main>
      </div>
    </>
  )
}
