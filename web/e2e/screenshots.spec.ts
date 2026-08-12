import type { Page } from '@playwright/test'
import { type TeamClient } from './support/api'
import { copyTrackingLink, expect, test } from './support/journey'
import { openCourierPhone, openRecipientPhone, signIn } from './support/devices'
import { freshnessSentence, mapShowingCourier } from './support/recipient'
import { COURIER, COURIER_AT_PICKUP, DISPATCHER } from './support/team'
import {
  confirmPickup,
  createDeliveryThroughTheForm,
  directAssign,
  goOnDutyAndShare,
  returnToWorkspace,
  startSharing,
} from './support/workspace'

/**
 * The three screenshots in `docs/screenshots/`, taken by driving the product rather than by hand.
 *
 * Hand-taken screenshots go stale silently: the UI changes, the picture in the README does not, and
 * a reader is looking at a version of the application that no longer exists. Regenerating these is
 * one command, so the honest thing is to make them cheap to redo rather than to promise to remember.
 *
 * It is excluded from `npm run e2e` — see `testIgnore` in `playwright.config.ts`. The journeys are
 * evidence and run on every push; this writes files into `docs/` and runs when somebody asks for it:
 *
 *   TRACKING_MAP_STYLE_URL=http://127.0.0.1:9099/style.json docker compose up --build --wait
 *   npm --prefix web run screenshots
 *
 * Everything it shows is fictional, and deliberately: `support/team.ts` invents the place, the two
 * accounts are the ones Flyway seeds, and the Courier stands where a browser was told to say it is.
 */

const OUTPUT = '../docs/screenshots'

/**
 * A Reference a reader can believe, unlike the timestamped ones the journeys generate.
 *
 * It is fixed rather than unique, which means this runs against a database that has not taken it
 * yet — a fresh `docker compose up` after `down -v`, or a deployment that has just been reset. That
 * is the right constraint for a screenshot: a picture of the product should be a picture of it
 * doing the demo, not of one run's accumulated leftovers. `freshDatabase` below says so plainly
 * rather than letting the form fail with "reference taken".
 */
const REFERENCE = 'DEMO-1001'

test('captures the Dispatcher, Courier and Recipient surfaces', async ({ page, browser, dispatcher }) => {
  await freshDatabase(dispatcher)

  await signIn(page, DISPATCHER)
  const deliveryId = await createDeliveryThroughTheForm(page, REFERENCE)

  const courierPhone = await openCourierPhone(browser, COURIER_AT_PICKUP)
  const recipientPhone = await openRecipientPhone(browser)

  try {
    await goOnDutyAndShare(courierPhone.page, COURIER)

    await test.step('the Dispatcher, with a Courier to assign', async () => {
      await page.goto(`/deliveries/${deliveryId}`)
      await page.getByRole('button', { name: 'Refresh recommendation' }).click()
      // Captured before the press, not after: the recommendation is the part of this screen worth
      // showing, and it is gone the moment the Delivery is assigned.
      await expect(page.getByRole('button', { name: `Direct assign ${COURIER.displayName}` })).toBeVisible()
      await capture(page, 'dispatcher-delivery-detail.png')
    })

    await directAssign(page, COURIER.displayName)

    await test.step('the Courier, on duty and sharing, holding the Delivery', async () => {
      // A reload is how this page learns it was assigned, and it ends the sharing session with it —
      // the reporting secret lives only in the page that started it. So sharing is started again
      // before the picture is taken, which is exactly what a Courier would do.
      await returnToWorkspace(courierPhone.page)
      await expect(courierPhone.page.getByText(REFERENCE)).toBeVisible()
      await startSharing(courierPhone.page)
      await capture(courierPhone.page, 'courier-workspace.png')
    })

    await confirmPickup(courierPhone.page)

    await test.step('the Recipient, following an In Transit Delivery', async () => {
      const link = await copyTrackingLink(dispatcher, deliveryId)
      await recipientPhone.page.goto(link.url)
      await expect(mapShowingCourier(recipientPhone.page)).toBeVisible()
      await expect(freshnessSentence(recipientPhone.page)).toHaveText(/^Live location/)
      await capture(recipientPhone.page, 'recipient-tracking.png')
    })
  } finally {
    await courierPhone.context.close()
    await recipientPhone.context.close()
  }
})

/**
 * The visible page rather than `fullPage`, because two of these three are phones and what a phone
 * shows without scrolling is the claim being made about the design.
 *
 * Scrolled to the top first: pressing a button part-way down a page leaves the viewport wherever
 * that button was, and a screenshot taken there starts mid-sentence.
 */
async function capture(page: Page, file: string): Promise<void> {
  await page.evaluate(() => window.scrollTo(0, 0))
  await page.screenshot({ path: `${OUTPUT}/${file}`, animations: 'disabled' })
}

/**
 * Refuses to run against a database that already holds the demo, instead of letting the Dispatcher's
 * form fail on a taken Reference three steps later.
 */
async function freshDatabase(dispatcher: TeamClient): Promise<void> {
  const existing = await dispatcher.get<{ reference: string }[]>('/api/deliveries')

  expect(
    existing.some((delivery) => delivery.reference === REFERENCE),
    `${REFERENCE} already exists, so this capture would photograph an earlier run.\n` +
      'Start from a fresh database:\n' +
      '  docker compose down -v\n' +
      '  TRACKING_MAP_STYLE_URL=http://127.0.0.1:9099/style.json docker compose up --build --wait',
  ).toBe(false)
}
