import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { Courier } from './courier'
import { fetchCourier, setDuty } from './courier'
import type { CancelDeliveryInput, CreateDeliveryInput, DeliveryDetail } from './deliveries'
import { cancelDelivery, createDelivery, fetchDeliveries, fetchDelivery } from './deliveries'
import type { Credentials } from './session'
import { fetchSession, signIn, signOut } from './session'

/** All API state lives in these query keys; there is no second global store. */
export const queryKeys = {
  session: ['session'] as const,
  deliveries: ['deliveries'] as const,
  delivery: (id: string) => ['deliveries', id] as const,
  courier: ['courier'] as const,
}

export function useSession() {
  return useQuery({ queryKey: queryKeys.session, queryFn: fetchSession, retry: false })
}

export function useSignIn() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (credentials: Credentials) => signIn(credentials),
    onSuccess: () => queryClient.invalidateQueries(),
  })
}

export function useSignOut() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => signOut(),
    onSuccess: () => {
      // Writing the empty session tells every mounted observer the session ended, which sends the
      // guarded routes to sign-in. Clearing the cache alone would not notify them, leaving the
      // previous page on screen.
      queryClient.setQueryData(queryKeys.session, null)
      queryClient.removeQueries({ queryKey: queryKeys.deliveries })
      // Signing out ended Location Sharing on the server, so the cached duty and freshness are
      // already wrong; the next Courier to sign in must read them again.
      queryClient.removeQueries({ queryKey: queryKeys.courier })
    },
  })
}

export function useCourier() {
  return useQuery({ queryKey: queryKeys.courier, queryFn: fetchCourier, retry: false })
}

export function useSetDuty() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (onDuty: boolean) => setDuty(onDuty),
    onSuccess: (courier: Courier) => queryClient.setQueryData(queryKeys.courier, courier),
  })
}

export function useDeliveries() {
  return useQuery({ queryKey: queryKeys.deliveries, queryFn: fetchDeliveries, retry: false })
}

export function useDelivery(id: string) {
  return useQuery({ queryKey: queryKeys.delivery(id), queryFn: () => fetchDelivery(id), retry: false })
}

export function useCreateDelivery() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateDeliveryInput) => createDelivery(input),
    onSuccess: (created: DeliveryDetail) => {
      queryClient.setQueryData(queryKeys.delivery(created.id), created)
      // Exact, because the fresh detail was just written above and does not need refetching.
      return queryClient.invalidateQueries({ queryKey: queryKeys.deliveries, exact: true })
    },
  })
}

export function useCancelDelivery(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CancelDeliveryInput) => cancelDelivery(id, input),
    onSuccess: (cancelled: DeliveryDetail) => {
      queryClient.setQueryData(queryKeys.delivery(id), cancelled)
      // Exact, because the fresh detail was just written above and does not need refetching.
      return queryClient.invalidateQueries({ queryKey: queryKeys.deliveries, exact: true })
    },
  })
}
