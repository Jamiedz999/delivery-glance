import { test as base, expect } from '@playwright/test'
import { TeamClient } from './api'
import { COURIER, DISPATCHER, HANDOFF, PICKUP } from './team'

/**
 * What every journey starts from: two signed-in team members, and a Courier who is free.
 *
 * The last part is the whole reason this file exists. The Delivery Team is two accounts, the one
 * Courier may hold one Active Delivery at a time, and a run that ended badly — a failed assertion,
 * an interrupted debug session — leaves that Courier holding one. The next run would then fail at
 * assignment, blaming the product for the previous run's exit.
 *
 * So each journey opens by finishing whatever it finds, through the same two endpoints the Courier
 * workspace calls when a human presses the buttons. Nothing here is a reset endpoint, and there is
 * no state a test can reach that a signed-in Courier could not.
 */

interface JourneyFixtures {
  dispatcher: TeamClient
  courier: TeamClient
  /** Applied to every test before its body runs; nothing needs to reference it. */
  freeCourier: void
}

export const test = base.extend<JourneyFixtures>({
  dispatcher: async ({ playwright, baseURL }, use) => {
    const client = await TeamClient.signIn(playwright, requireBaseURL(baseURL), DISPATCHER)
    await use(client)
    await client.dispose()
  },

  courier: async ({ playwright, baseURL }, use) => {
    const client = await TeamClient.signIn(playwright, requireBaseURL(baseURL), COURIER)
    await use(client)
    await client.dispose()
  },

  freeCourier: [
    async ({ courier }, use) => {
      await releaseCourier(courier)
      await use()
    },
    { auto: true },
  ],
})

export { expect }

export interface CourierDelivery {
  id: string
  reference: string
  state: 'ASSIGNED' | 'IN_TRANSIT'
  version: number
}

export interface DeliveryDetail {
  id: string
  reference: string
  state: string
  version: number
}

export interface CopiedLink {
  url: string
  expiresAt: string
}

/**
 * Drives any Delivery the Courier is still holding to Delivered, then ends sharing and duty.
 *
 * Delivered rather than Cancelled because Cancelled is refused after pickup, and a Delivery left
 * In Transit is exactly the state an interrupted run is most likely to leave behind. Handing it
 * over is the one exit the Courier's own workspace offers for it.
 */
export async function releaseCourier(courier: TeamClient): Promise<void> {
  let current = await courier.get<CourierDelivery | undefined>('/api/couriers/me/deliveries/current')

  if (current?.state === 'ASSIGNED') {
    await progress(courier, current, 'pickup')
    current = await courier.get<CourierDelivery | undefined>('/api/couriers/me/deliveries/current')
  }
  if (current?.state === 'IN_TRANSIT') {
    await progress(courier, current, 'handoff')
  }

  await courier.delete('/api/couriers/me/location-sharing')
  await courier.put('/api/couriers/me/duty', { onDuty: false })
}

async function progress(
  courier: TeamClient,
  delivery: CourierDelivery,
  action: 'pickup' | 'handoff',
): Promise<void> {
  await courier.post(`/api/couriers/me/deliveries/${delivery.id}/${action}`, {
    commandId: crypto.randomUUID(),
    expectedVersion: delivery.version,
  })
}

/** The fictional Delivery both journeys carry, created the way the Dispatcher's form creates one. */
export async function createDelivery(dispatcher: TeamClient, reference: string): Promise<DeliveryDetail> {
  return dispatcher.post<DeliveryDetail>('/api/deliveries', {
    reference,
    pickup: PICKUP,
    handoff: HANDOFF,
  })
}

export async function copyTrackingLink(dispatcher: TeamClient, deliveryId: string): Promise<CopiedLink> {
  return dispatcher.post<CopiedLink>(`/api/deliveries/${deliveryId}/tracking-link/copy`)
}

interface Recommendation {
  candidates: { courierId: string; displayName: string }[]
}

/**
 * Direct Assignment as setup rather than as subject.
 *
 * The happy path presses the button and asserts what the Dispatcher sees; a journey that is about
 * something else says so by not pretending to test assignment again on the way past. The
 * recommendation read is still real, so a Courier who is not eligible fails here, where the message
 * names the reason.
 */
export async function assignNearestCourier(dispatcher: TeamClient, delivery: DeliveryDetail): Promise<void> {
  const recommendation = await dispatcher.get<Recommendation>(
    `/api/deliveries/${delivery.id}/courier-recommendations`,
  )
  const nearest = recommendation.candidates[0]
  expect(nearest, 'no Courier was eligible, so there was nobody to assign').toBeDefined()

  await dispatcher.post(`/api/deliveries/${delivery.id}/assignment`, {
    courierId: nearest.courierId,
    expectedVersion: delivery.version,
    commandId: crypto.randomUUID(),
  })
}

function requireBaseURL(baseURL: string | undefined): string {
  if (baseURL === undefined) {
    throw new Error('No baseURL is configured; these journeys need a running application to talk to.')
  }
  return baseURL
}
