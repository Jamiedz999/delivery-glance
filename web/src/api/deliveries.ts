import { apiRequest } from './http'

export type DeliveryState = 'AWAITING_COURIER' | 'CANCELLED'

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
  CANCELLED: 'Cancelled',
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
