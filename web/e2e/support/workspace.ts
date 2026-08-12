import { type Page, expect } from '@playwright/test'
import { HANDOFF, PICKUP } from './team'

/**
 * The steps a journey performs by pressing things, expressed once.
 *
 * Everything here goes through the control a human would use, and asserts the state the workspace
 * actually reports afterwards rather than assuming the click worked. A helper that clicks and
 * returns immediately would push its failures into whichever later assertion happened to be first.
 */

/** Fills the Dispatcher's form and returns the identifier of the Delivery it created. */
export async function createDelivery(page: Page, reference: string): Promise<string> {
  await page.goto('/deliveries/new')
  await page.getByLabel('Delivery reference').fill(reference)

  const pickup = page.getByRole('group', { name: 'Pickup address' })
  await pickup.getByLabel('Address').fill(PICKUP.addressLabel)
  await pickup.getByLabel('Latitude').fill(String(PICKUP.latitude))
  await pickup.getByLabel('Longitude').fill(String(PICKUP.longitude))

  const handoff = page.getByRole('group', { name: 'Handoff address' })
  await handoff.getByLabel('Address').fill(HANDOFF.addressLabel)
  await handoff.getByLabel('Latitude').fill(String(HANDOFF.latitude))
  await handoff.getByLabel('Longitude').fill(String(HANDOFF.longitude))

  await page.getByRole('button', { name: 'Create delivery' }).click()

  await expect(page.getByRole('heading', { name: reference })).toBeVisible()
  const id = new URL(page.url()).pathname.split('/').pop()
  expect(id, 'the detail page URL should carry the new Delivery identifier').toBeTruthy()
  return id as string
}

export async function goOnDuty(page: Page): Promise<void> {
  await page.goto('/courier')
  await page.getByRole('button', { name: 'Go on duty' }).click()
  await expect(page.getByText('On duty', { exact: true })).toBeVisible()
}

/**
 * Presses Start and waits until the server actually holds a position.
 *
 * The wait is on the Courier's own "Position held by the server" line rather than on a request,
 * because that line is the product's answer to "is anything being shared", and a journey that
 * assigned before it appeared would be racing the first report.
 */
export async function startSharing(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Start sharing' }).click()
  await expect(page.getByText('Sharing your position')).toBeVisible()
  await expect(page.getByText(/^Live — measured/)).toBeVisible()
}

/**
 * Comes back to the Courier workspace to see what changed while it was open.
 *
 * A reload, because that is the only thing a Courier can do that makes this page read the server
 * again: the workspace does not poll, and React Query's focus refetch needs a `visibilitychange`
 * the browser only fires when the tab actually goes away. The README's walkthrough works because
 * the human there switches browser profiles; a Courier watching their own page — which is what
 * foreground sharing asks of them — is never told they were assigned. That gap is recorded in
 * `docs/planning/implementation/INCIDENTAL-FINDINGS.md`; adding polling would be new product
 * behaviour and belongs to refinement, not to this test.
 *
 * Sharing is off afterwards, and asserting that here is not a workaround dressed up as a check:
 * the reporting secret lives only in the page that started the session, which is the promise that
 * a reloaded page cannot silently resume reporting.
 */
export async function returnToWorkspace(page: Page): Promise<void> {
  await page.reload()
  await expect(page.getByText('Sharing off')).toBeVisible()
}

export async function stopSharing(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Stop sharing' }).click()
  await expect(page.getByText('Sharing off')).toBeVisible()
}

export async function directAssign(page: Page, courierDisplayName: string): Promise<void> {
  await page.getByRole('button', { name: 'Refresh recommendation' }).click()
  await page.getByRole('button', { name: `Direct assign ${courierDisplayName}` }).click()
  await expect(page.getByText('Assigned to')).toBeVisible()
}

export async function confirmPickup(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Confirm pickup' }).click()
  // The button the workspace offers is the state it is in: only an In Transit Delivery can be
  // handed over, so this is the same assertion as reading the status line, without depending on
  // how the reference and the status share one sentence.
  await expect(page.getByRole('button', { name: 'Confirm handoff' })).toBeVisible()
}

export async function confirmHandoff(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Confirm handoff' }).click()
  await expect(page.getByText('No Delivery is currently assigned to you.')).toBeVisible()
}
