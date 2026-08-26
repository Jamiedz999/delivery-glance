import { apiRequest } from './http'

export type DeliveryState = 'AWAITING_COURIER' | 'ASSIGNED' | 'IN_TRANSIT' | 'DELIVERED' | 'CANCELLED'

export const CANCELLATION_REASONS = [
  { value: 'NO_LONGER_REQUIRED', label: 'Delivery no longer required' },
  { value: 'INVALID_DELIVERY_DETAILS', label: 'Invalid delivery details' },
  { value: 'ITEM_UNAVAILABLE_AT_PICKUP', label: 'Item unavailable at pickup' },
  { value: 'OTHER', label: 'Other (note required)' },
] as const

export type CancellationReason = (typeof CANCELLATION_REASONS)[number]['value']

export interface Address {
  addressLabel: string
  latitude: number
  longitude: number
}

export interface DeliverySummary {
  id: string
  reference: string
  state: DeliveryState
  version: number
  pickupAddressLabel: string
  handoffAddressLabel: string
  createdAt: string
  updatedAt: string
}

export interface DeliveryTransition {
  previousState: DeliveryState | null
  nextState: DeliveryState
  actorDisplayName: string
  reasonCode: CancellationReason | null
  reasonNote: string | null
  occurredAt: string
}

export interface DeliveryDetail {
  id: string
  reference: string
  state: DeliveryState
  version: number
  pickup: Address
  handoff: Address
  createdAt: string
  updatedAt: string
  transitions: DeliveryTransition[]
  assignment: {
    courierId: string
    courierDisplayName: string
    assignedAt: string
  } | null
}

export interface CourierRecommendation {
  calculatedAt: string
  candidates: {
    courierId: string
    displayName: string
    distanceMetres: number
  }[]
}

export interface AssignCourierInput {
  courierId: string
  expectedVersion: number
  commandId: string
}

export interface CourierDelivery {
  id: string
  reference: string
  state: 'ASSIGNED' | 'IN_TRANSIT'
  version: number
  pickupAddressLabel: string
  handoffAddressLabel: string
}

export interface ProgressDeliveryInput {
  commandId: string
  expectedVersion: number
}

/**
 * What copying a Tracking Link hands back: the raw capability URL and when it stops working.
 *
 * The `url` is a live secret — the one place in the application that holds a usable capability. It is
 * written to the clipboard and nowhere else; it must never be bound into rendered markup, React
 * state or the browser history. Only `expiresAt` is safe to show.
 */
export interface CopiedTrackingLink {
  url: string
  expiresAt: string
}

/**
 * A coordinate the Dispatcher has not filled in yet is sent as null, so the server answers with the
 * same field-level message it gives any other invalid point.
 */
export interface AddressInput {
  addressLabel: string
  latitude: number | null
  longitude: number | null
}

export interface CreateDeliveryInput {
  reference: string
  pickup: AddressInput
  handoff: AddressInput
}

export interface CancelDeliveryInput {
  /** Reused when a cancellation is retried, so the retry cannot cancel twice. */
  commandId: string
  expectedVersion: number
  reason: CancellationReason
  note: string | null
}

export const DELIVERY_STATE_LABELS: Record<DeliveryState, string> = {
  AWAITING_COURIER: 'Awaiting courier',
  ASSIGNED: 'Assigned',
  IN_TRANSIT: 'In transit',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
}

/**
 * Whether a Delivery has reached a state it can no longer leave. Delivered and Cancelled are the two
 * terminal states the frontend models, and several places gate on the same predicate — the final-state
 * note, and whether a Tracking Link is still worth copying — so the concept lives here under one name.
 */
export function isTerminalState(state: DeliveryState): boolean {
  return state === 'DELIVERED' || state === 'CANCELLED'
}

/** The `.status-chip` modifier class each lifecycle state renders with. */
export const DELIVERY_STATE_CHIP_CLASS: Record<DeliveryState, string> = {
  AWAITING_COURIER: 'is-awaiting',
  ASSIGNED: 'is-assigned',
  IN_TRANSIT: 'is-transit',
  DELIVERED: 'is-delivered',
  CANCELLED: 'is-cancelled',
}

export function fetchDeliveries(): Promise<DeliverySummary[]> {
  return apiRequest<DeliverySummary[]>('/api/deliveries')
}

export function fetchDelivery(id: string): Promise<DeliveryDetail> {
  return apiRequest<DeliveryDetail>(`/api/deliveries/${id}`)
}

export function createDelivery(input: CreateDeliveryInput): Promise<DeliveryDetail> {
  return apiRequest<DeliveryDetail>('/api/deliveries', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function cancelDelivery(id: string, input: CancelDeliveryInput): Promise<DeliveryDetail> {
  return apiRequest<DeliveryDetail>(`/api/deliveries/${id}/cancel`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function fetchCourierRecommendation(id: string): Promise<CourierRecommendation> {
  return apiRequest<CourierRecommendation>(`/api/deliveries/${id}/courier-recommendations`)
}

export function assignCourier(id: string, input: AssignCourierInput): Promise<void> {
  return apiRequest<void>(`/api/deliveries/${id}/assignment`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export async function fetchCurrentCourierDelivery(): Promise<CourierDelivery | null> {
  return (await apiRequest<CourierDelivery | undefined>('/api/couriers/me/deliveries/current')) ?? null
}

export function copyTrackingLink(id: string): Promise<CopiedTrackingLink> {
  return apiRequest<CopiedTrackingLink>(`/api/deliveries/${id}/tracking-link/copy`, {
    method: 'POST',
  })
}

export function progressCourierDelivery(
  deliveryId: string,
  action: 'pickup' | 'handoff',
  input: ProgressDeliveryInput,
): Promise<void> {
  return apiRequest<void>(`/api/couriers/me/deliveries/${deliveryId}/${action}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}
