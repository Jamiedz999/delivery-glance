import type { Locator, Page } from '@playwright/test'

/**
 * The Recipient page, named the way a reader meets it.
 *
 * The two markers are the point of this file. MapLibre draws them as elements over the canvas and
 * labels them, so whether a Courier is on the map is a question Playwright can actually answer —
 * and it is the question the degradation journey exists to ask. Both are `role="img"` and so is the
 * map they sit on, which is why every one of these is anchored to its exact name.
 */
export const courierMarker = (page: Page): Locator =>
  page.getByRole('img', { name: 'Courier’s last reported position', exact: true })

export const handoffMarker = (page: Page): Locator =>
  page.getByRole('img', { name: 'Handoff address', exact: true })

export const mapShowingCourier = (page: Page): Locator =>
  page.getByRole('img', {
    name: 'Map of the handoff address and the courier’s last reported position',
    exact: true,
  })

export const mapWithoutCourier = (page: Page): Locator =>
  page.getByRole('img', { name: 'Map of the handoff address', exact: true })

/** The sentence that qualifies the map, and the whole of what the page claims about the reading. */
export const freshnessSentence = (page: Page): Locator => page.locator('p.freshness')

/** Whether the page is still hearing about changes — a different question from the one above. */
export const connectionSentence = (page: Page): Locator => page.locator('p.connection')
