import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { AppLayout } from './components/AppLayout'
import { RequireRole } from './components/RequireRole'
import { useSession } from './api/queries'
import { ConnectionPage } from './pages/ConnectionPage'
import { CourierHomePage } from './pages/CourierHomePage'
import { CreateDeliveryPage } from './pages/CreateDeliveryPage'
import { DeliveryDetailPage } from './pages/DeliveryDetailPage'
import { DeliveryListPage } from './pages/DeliveryListPage'
import { SignInPage } from './pages/SignInPage'

const queryClient = new QueryClient()

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<HomePage />} />
        <Route path="sign-in" element={<SignInPage />} />
        <Route
          path="deliveries"
          element={
            <RequireRole role="DISPATCHER">
              <DeliveryListPage />
            </RequireRole>
          }
        />
        <Route
          path="deliveries/new"
          element={
            <RequireRole role="DISPATCHER">
              <CreateDeliveryPage />
            </RequireRole>
          }
        />
        <Route
          path="deliveries/:id"
          element={
            <RequireRole role="DISPATCHER">
              <DeliveryDetailPage />
            </RequireRole>
          }
        />
        <Route
          path="courier"
          element={
            <RequireRole role="COURIER">
              <CourierHomePage />
            </RequireRole>
          }
        />
        <Route path="system" element={<ConnectionPage />} />
        <Route path="*" element={<p role="alert">That page does not exist.</p>} />
      </Route>
    </Routes>
  )
}

/** Sends each signed-in account to its own workspace, and everyone else to sign-in. */
function HomePage() {
  const { data: session, isPending, isError } = useSession()

  if (isPending) {
    return <p role="status">Checking your session…</p>
  }
  if (isError) {
    return <p role="alert">Could not reach the Delivery Glance API.</p>
  }
  if (session === null) {
    return <Navigate to="/sign-in" replace />
  }
  return <Navigate to={session.role === 'DISPATCHER' ? '/deliveries' : '/courier'} replace />
}

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </QueryClientProvider>
  )
}

export default App
