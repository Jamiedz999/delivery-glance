import { createDelivery, expect, test } from './support/journey'
import { openCourierPhone, signIn } from './support/devices'
import { COURIER, COURIER_AT_PICKUP, DISPATCHER, reference } from './support/team'
import { goOnDutyAndShare } from './support/workspace'

/**
 * The states nobody demos: loading, empty, not-found and losing a race.
 *
 * Each one has the same two ways to be wrong, and they are the reason this file is separate from
 * the journeys. It can **claim success** — showing a page that looks like a finished answer when
 * the answer has not arrived, or telling a Dispatcher their assignment worked when somebody else's
 * won. Or it can **leak**: a "does not exist" that names what does not exist has answered the
 * question it was refusing.
 */

test('a page that is still loading does not look like an answer', async ({ page }) => {
  await signIn(page, DISPATCHER)

  // Held open, because the interesting moment is the one that normally lasts a few milliseconds.
  let release: (() => void) | null = null
  await page.route('**/api/deliveries', async (route) => {
    await new Promise<void>((wake) => {
      release = wake
    })
    await route.continue()
  })

  await page.goto('/deliveries')

  await expect(page.getByText('Loading deliveries…')).toBeVisible()
  // Not an empty list, and not a table with no rows: either would be a page saying "there are no
  // Deliveries" about a question nobody has answered yet.
  await expect(page.getByRole('table')).toBeHidden()
  await expect(page.getByText('No deliveries yet.')).toBeHidden()

  release!()
  await expect(page.getByRole('table')).toBeVisible()
})

test('a Delivery that does not exist is refused without describing anything', async ({ page }) => {
  await signIn(page, DISPATCHER)

  // A well-formed identifier for nothing. A malformed one would be refused by whichever check ran
  // first, which is not the refusal worth proving.
  await page.goto('/deliveries/3d0f6b1a-0000-4000-8000-000000000000')

  await expect(page.getByRole('alert')).toHaveText('That delivery does not exist.')
  await expect(page.getByRole('heading', { level: 1 })).toBeHidden()
  await expect(page.locator('main')).not.toContainText('3d0f6b1a')
})

test('a Courier with nothing assigned is told so, and shown nobody else’s Delivery', async ({
  browser,
  dispatcher,
}) => {
  const deliveryReference = reference('empty')
  const courierPhone = await openCourierPhone(browser, COURIER_AT_PICKUP)

  try {
    // A Delivery exists and is nothing to do with this Courier, which is the case an empty state
    // can get wrong in the way that matters.
    await createDelivery(dispatcher, deliveryReference)
    await signIn(courierPhone.page, COURIER)
    await courierPhone.page.goto('/courier')

    await expect(courierPhone.page.getByText('No Delivery is currently assigned to you.')).toBeVisible()
    await expect(courierPhone.page.locator('main')).not.toContainText(deliveryReference)
  } finally {
    await courierPhone.context.close()
  }
})

test('the Dispatcher who loses a Direct Assignment is told, not congratulated', async ({
  page,
  browser,
  dispatcher,
}) => {
  const deliveryReference = reference('conflict')
  const courierPhone = await openCourierPhone(browser, COURIER_AT_PICKUP)
  // A second window on the same Delivery, which is how one Dispatcher's page ends up holding a
  // version another Dispatcher has already moved past.
  const second = await browser.newContext({ locale: 'en-GB', timezoneId: 'UTC' })

  try {
    const delivery = await createDelivery(dispatcher, deliveryReference)
    await goOnDutyAndShare(courierPhone.page, COURIER)

    const loser = await second.newPage()
    await signIn(loser, DISPATCHER)
    await signIn(page, DISPATCHER)

    for (const workspace of [page, loser]) {
      await workspace.goto(`/deliveries/${delivery.id}`)
      await workspace.getByRole('button', { name: 'Refresh recommendation' }).click()
      await expect(
        workspace.getByRole('button', { name: `Direct assign ${COURIER.displayName}` }),
      ).toBeVisible()
    }

    await page.getByRole('button', { name: `Direct assign ${COURIER.displayName}` }).click()
    await expect(page.getByText('Assigned to')).toBeVisible()

    // The second window is still showing the button, and still holding the version it read.
    await loser.getByRole('button', { name: `Direct assign ${COURIER.displayName}` }).click()

    await expect(loser.getByRole('alert')).toHaveText(
      'Another assignment changed this Delivery. Reload it to see the winner.',
    )
    // The Delivery is genuinely Assigned, and this page is allowed to say so — what it must not do
    // is present that as the outcome of the press it just refused.
    await expect(loser.getByText('Assigned to')).toBeVisible()
  } finally {
    await second.close()
    await courierPhone.context.close()
  }
})
