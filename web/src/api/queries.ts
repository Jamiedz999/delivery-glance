import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { Courier } from './courier'
import { fetchCourier, setDuty } from './courier'
import type {
  AssignCourierInput,
  CancelDeliveryInput,
  CreateDeliveryInput,
  DeliveryDetail,
  ProgressDeliveryInput,
} from './deliveries'
import {
  assignCourier,
  cancelDelivery,
  copyTrackingLink,
  createDelivery,
  fetchCourierRecommendation,
  fetchCurrentCourierDelivery,
  fetchDeliveries,
  fetchDelivery,
  progressCourierDelivery,
} from './deliveries'
import type { Credentials } from './session'
import { fetchSession, signIn, signOut } from './session'

/** All API state lives in these query keys; there is no second global store. */
export const queryKeys = {
  session: ['session'] as const,
  deliveries: ['deliveries'] as const,
  delivery: (id: string) => ['deliveries', id] as const,
  recommendation: (id: string) => ['deliveries', id, 'courier-recommendation'] as const,
  courier: ['courier'] as const,
  currentCourierDelivery: ['current-courier-delivery'] as const,
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
      queryClient.removeQueries({ queryKey: queryKeys.currentCourierDelivery })
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

export function useCourierRecommendation(id: string) {
  return useQuery({
    queryKey: queryKeys.recommendation(id),
    queryFn: () => fetchCourierRecommendation(id),
    retry: false,
  })
}

export function useAssignCourier(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: AssignCourierInput) => assignCourier(id, input),
    onSettled: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.delivery(id), exact: true }),
        queryClient.invalidateQueries({ queryKey: queryKeys.deliveries, exact: true }),
        queryClient.invalidateQueries({ queryKey: queryKeys.recommendation(id), exact: true }),
      ])
    },
  })
}

/**
 * Copy a Delivery's Tracking Link to the clipboard, surfacing only its expiry to the caller.
 *
 * The raw URL the endpoint returns is a live capability. It is written to the clipboard inside the
 * mutation and deliberately never returned: the caller — and therefore React Query's cache, the
 * rendered page and its history — only ever sees `expiresAt`. There is no query to invalidate; the
 * server records who copied and when, but the Delivery the page reads is unchanged.
 */
export function useCopyTrackingLink(id: string) {
  return useMutation({
    mutationFn: async (): Promise<{ expiresAt: string }> => {
      const { url, expiresAt } = await copyTrackingLink(id)
      await navigator.clipboard.writeText(url)
      return { expiresAt }
    },
  })
}

export function useCurrentCourierDelivery() {
  return useQuery({
    queryKey: queryKeys.currentCourierDelivery,
    queryFn: fetchCurrentCourierDelivery,
    retry: false,
  })
}

export function useProgressCourierDelivery(action: 'pickup' | 'handoff') {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ deliveryId, input }: { deliveryId: string; input: ProgressDeliveryInput }) =>
      progressCourierDelivery(deliveryId, action, input),
    onSettled: () =>
      queryClient.invalidateQueries({ queryKey: queryKeys.currentCourierDelivery, exact: true }),
  })
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
