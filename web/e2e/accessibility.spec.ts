import AxeBuilder from '@axe-core/playwright'
import type { Result } from 'axe-core'
import type { Page } from '@playwright/test'
import { assignNearestCourier, copyTrackingLink, createDelivery, expect, test } from './support/journey'
import { openCourierPhone, openRecipientPhone } from './support/devices'
import { COURIER, COURIER_AT_PICKUP, DISPATCHER, HANDOFF, PICKUP, reference } from './support/team'
import { confirmPickup, goOnDutyAndShare, returnToWorkspace, startSharing } from './support/workspace'
import { signIn } from './support/devices'

/**
 * The three surfaces a person actually uses, checked where they are used: the Dispatcher at a desk,
 * the Courier on a phone, the Recipient on a phone they did not choose to be holding.
 *
 * Automated rules cannot tell whether a page is usable; they can tell whether it is unusable in the
 * ways machines can see, which is worth having and is not the same claim. What they miss is covered
 * by the keyboard walkthrough below, which is a person's route through the product written down.
 */

/** Serious and critical only. Anything below is real but is not what "no violations" should mean. */
const BLOCKING = new Set(['serious', 'critical'])

async function violationsOn(page: Page): Promise<Result[]> {
  const { violations } = await new AxeBuilder({ page }).analyze()
  return violations.filter((violation) => BLOCKING.has(violation.impact ?? ''))
}

function describeViolations(surface: string, violations: Result[]): string {
  return violations
    .map(
      (violation) =>
        `${surface}: ${violation.id} (${violation.impact}) — ${violation.help}\n` +
        violation.nodes.map((node) => `    ${node.target.join(' ')}`).join('\n'),
    )
    .join('\n')
}

test('the three main surfaces carry no serious or critical automated violations', async ({
  page,
  browser,
  dispatcher,
}) => {
  const deliveryReference = reference('a11y')
  const courierPhone = await openCourierPhone(browser, COURIER_AT_PICKUP)
  // The Recipient's phone asks for reduced motion, because that is the setting a vestibular
  // disorder puts on it and because this is the one journey that reaches a drawn map.
  const recipientPhone = await openRecipientPhone(browser, { reducedMotion: 'reduce' })
  const found: string[] = []

  try {
    const delivery = await createDelivery(dispatcher, deliveryReference)
    await goOnDutyAndShare(courierPhone.page, COURIER)

    await test.step('the Dispatcher at a desk, on the list and on one Delivery', async () => {
      await signIn(page, DISPATCHER)
      await page.goto('/deliveries')
      await expect(page.getByRole('heading', { name: 'Deliveries' })).toBeVisible()
      found.push(describeViolations('Dispatcher list', await violationsOn(page)))

      await page.goto(`/deliveries/${delivery.id}`)
      // Scanned with the recommendation on screen, because that panel is the part of the workspace
      // a Dispatcher spends their attention on and the only one that is a list of actions.
      await expect(page.getByRole('button', { name: `Direct assign ${COURIER.displayName}` })).toBeVisible()
      found.push(describeViolations('Dispatcher delivery', await violationsOn(page)))
    })

    await test.step('the Courier on a phone, holding a Delivery and sharing', async () => {
      await assignNearestCourier(dispatcher, delivery)
      await returnToWorkspace(courierPhone.page)
      await startSharing(courierPhone.page)
      await expect(courierPhone.page.getByText(deliveryReference)).toBeVisible()
      found.push(describeViolations('Courier workspace', await violationsOn(courierPhone.page)))
    })

    await test.step('the Recipient on a phone, following an In Transit Delivery', async () => {
      await confirmPickup(courierPhone.page)
      const link = await copyTrackingLink(dispatcher, delivery.id)
      await recipientPhone.page.goto(link.url)
      // In Transit is the richest state this page ever reaches: headline, next step, reference,
      // address, courier, map and two different live regions. The states below it are subsets.
      await expect(recipientPhone.page.getByRole('heading', { name: 'Courier location' })).toBeVisible()
      found.push(describeViolations('Recipient tracking', await violationsOn(recipientPhone.page)))
    })

    await test.step('and nothing on it moves', async () => {
      // Asserted on this document rather than on the stylesheet, and asserted here rather than on a
      // simpler state, because this is the only page in the product that has a map on it — and a
      // map is the thing most likely to acquire an eased camera later. Nothing is behind a media
      // query today because nothing animates at all: the map is framed on its markers at load and
      // deliberately never moved afterwards.
      await expect(recipientPhone.page.getByRole('img', { name: /Map of the handoff/ })).toBeVisible()
      const moving = await recipientPhone.page.evaluate(() =>
        [...document.querySelectorAll('*')]
          .filter((element) => {
            const style = getComputedStyle(element)
            return (
              parseFloat(style.animationDuration) > 0 ||
              parseFloat(style.transitionDuration) > 0 ||
              style.animationName !== 'none'
            )
          })
          .map((element) => `${element.tagName.toLowerCase()}.${element.className}`),
      )
      expect(moving, 'elements that animate or transition').toEqual([])
    })

    expect(found.filter((report) => report !== '').join('\n'), 'automated accessibility violations').toBe('')
  } finally {
    await courierPhone.context.close()
    await recipientPhone.context.close()
  }
})

test('a Dispatcher can create a Delivery with the keyboard alone', async ({ page }) => {
  const deliveryReference = reference('keyboard')

  await test.step('sign in without touching the pointer', async () => {
    await page.goto('/sign-in')
    await page.getByLabel('Email').focus()
    await expectFocusIsVisible(page)
    await page.keyboard.type(DISPATCHER.email)
    await page.keyboard.press('Tab')
    await expectFocused(page, 'Password')
    await page.keyboard.type(DISPATCHER.password)
    await page.keyboard.press('Tab')
    await expectFocusIsVisible(page)
    await page.keyboard.press('Enter')
    await expect(page.getByText(DISPATCHER.displayName)).toBeVisible()
  })

  await test.step('reach the form from the list by tabbing to its link', async () => {
    await expect(page.getByRole('heading', { name: 'Deliveries' })).toBeVisible()
    await page.getByRole('link', { name: 'New delivery' }).focus()
    await expectFocusIsVisible(page)
    await page.keyboard.press('Enter')
    await expect(page.getByRole('heading', { name: 'New delivery' })).toBeVisible()
  })

  await test.step('fill every field in tab order and submit with Enter', async () => {
    await page.getByLabel('Delivery reference').focus()
    await page.keyboard.type(deliveryReference)

    // Straight down the form in the order the tab key offers it. If the reading order and the tab
    // order ever disagree, this walkthrough puts an address in a latitude field and fails.
    for (const value of [
      PICKUP.addressLabel,
      String(PICKUP.latitude),
      String(PICKUP.longitude),
      HANDOFF.addressLabel,
      String(HANDOFF.latitude),
      String(HANDOFF.longitude),
    ]) {
      await page.keyboard.press('Tab')
      await expectFocusIsVisible(page)
      await page.keyboard.type(value)
    }

    await page.keyboard.press('Tab')
    await expect(page.locator(':focus')).toHaveText('Create delivery')
    await page.keyboard.press('Enter')
  })

  await expect(page.getByRole('heading', { name: deliveryReference })).toBeVisible()
  await expect(page.getByText(PICKUP.addressLabel)).toBeVisible()
  await expect(page.getByText(HANDOFF.addressLabel)).toBeVisible()
})

async function expectFocused(page: Page, label: string): Promise<void> {
  await expect(page.getByLabel(label)).toBeFocused()
  await expectFocusIsVisible(page)
}

/**
 * That whatever holds focus is drawn as holding it.
 *
 * The application sets no focus styles of its own, so this is really a check that nothing has
 * removed the browser's — which is the usual way a keyboard user loses their place, and the kind of
 * change that arrives inside an unrelated stylesheet tidy-up.
 */
async function expectFocusIsVisible(page: Page): Promise<void> {
  const outline = await page.evaluate(() => {
    const focused = document.activeElement
    if (focused === null || focused === document.body) {
      return 'nothing is focused'
    }
    const style = getComputedStyle(focused)
    return `${style.outlineStyle} ${style.outlineWidth}`
  })
  expect(outline, 'the focused element should be drawn as focused').not.toMatch(/^(none|nothing)/)
}
