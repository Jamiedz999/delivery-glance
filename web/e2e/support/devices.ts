import { type Browser, type BrowserContext, type Page, devices, expect } from '@playwright/test'
import { type Account, type Position, REPORTED_ACCURACY_METRES } from './team'

/**
 * The phones in these journeys, and the desk the Dispatcher works at.
 *
 * A cross-role journey is several people at once, so the roles that are not the test's default
 * project open their own contexts here. Contexts made this way do not inherit `use` from
 * `playwright.config.ts`, which is why locale and time zone are repeated: a Recipient page renders
 * a handoff time in the reader's own zone, and an unpinned zone would make the assertion depend on
 * the machine running it.
 */
const READER = { locale: 'en-GB', timezoneId: 'UTC' } as const

const PHONE = { ...devices['Pixel 7'], ...READER }

export interface Device {
  context: BrowserContext
  page: Page
}

/**
 * The Courier's phone, with Geolocation already granted and standing where the journey says.
 *
 * Permission is granted up front because the product's consent story is about pressing Start, not
 * about the browser prompt: the page asks the browser for nothing until Start, and a prompt no
 * automated run can answer would only turn that into a timeout.
 */
export async function openCourierPhone(browser: Browser, standing: Position): Promise<Device> {
  const context = await browser.newContext({
    ...PHONE,
    permissions: ['geolocation'],
    geolocation: { ...standing, accuracy: REPORTED_ACCURACY_METRES },
  })
  return { context, page: await context.newPage() }
}

/** The Recipient's phone. It holds no account, no permission and nothing but the link. */
export async function openRecipientPhone(browser: Browser): Promise<Device> {
  const context = await browser.newContext(PHONE)
  return { context, page: await context.newPage() }
}

export async function moveCourierTo(device: Device, standing: Position): Promise<void> {
  await device.context.setGeolocation({ ...standing, accuracy: REPORTED_ACCURACY_METRES })
}

/** Signs in through the form, because that is the only way into either workspace. */
export async function signIn(page: Page, account: Account): Promise<void> {
  await page.goto('/sign-in')
  await page.getByLabel('Email').fill(account.email)
  await page.getByLabel('Password').fill(account.password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByText(account.displayName)).toBeVisible()
}
