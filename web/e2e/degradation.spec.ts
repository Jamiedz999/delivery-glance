import { assignNearestCourier, copyTrackingLink, createDelivery, expect, test } from './support/journey'
import { restartApplication } from './support/application'
import { openCourierPhone, openRecipientPhone, signIn } from './support/devices'
import {
  connectionSentence,
  courierMarker,
  freshnessSentence,
  handoffMarker,
  mapWithoutCourier,
} from './support/recipient'
import { COURIER, COURIER_AT_PICKUP, HANDOFF, reference } from './support/team'
import { confirmPickup, goOnDuty, returnToWorkspace, startSharing, stopSharing } from './support/workspace'

/**
 * Everything that goes wrong, and what the Recipient is told while it does.
 *
 * The claim under test is the uncomfortable half of the product: a page that keeps saying where
 * somebody is on the strength of a two-minute-old reading is worse than one that admits it does not
 * know. This journey stops the sharing, takes the stream away, ages the reading past the point
 * where it means anything, and checks that the marker leaves the map with nothing having arrived
 * from the server to cause it — the page's own clock is the only thing that could have.
 *
 * That clock is the browser's, moved by Playwright. Nothing in the application is aware of it: no
 * test profile, no override header, no injected time source. What is proved here is the behaviour
 * of the shipped build.
 */
test('the Recipient page stays honest as sharing stops, the stream drops and the reading expires', async ({
  browser,
  baseURL,
  dispatcher,
}) => {
  const deliveryReference = reference('degradation')
  const courierPhone = await openCourierPhone(browser, COURIER_AT_PICKUP)
  const recipientPhone = await openRecipientPhone(browser)

  try {
    const delivery = await createDelivery(dispatcher, deliveryReference)

    await test.step('a Delivery is in transit with a live position', async () => {
      await signIn(courierPhone.page, COURIER)
      await goOnDuty(courierPhone.page)
      await startSharing(courierPhone.page)
      await assignNearestCourier(dispatcher, delivery)
      await returnToWorkspace(courierPhone.page)
      await startSharing(courierPhone.page)
      await confirmPickup(courierPhone.page)
    })

    await test.step('the Recipient opens the link and the courier is on the map', async () => {
      // Installed before the first navigation, which is the only point a page's clock can be
      // replaced, and immediately resumed so the page runs at ordinary speed until this journey
      // decides otherwise. A frozen clock would also freeze MapLibre's render loop, and a map that
      // never finished loading would look exactly like a map that removed its marker.
      await recipientPhone.page.clock.install()
      await recipientPhone.page.clock.resume()

      const link = await copyTrackingLink(dispatcher, delivery.id)
      await recipientPhone.page.goto(link.url)

      await expect(
        recipientPhone.page.getByRole('heading', { name: 'Your delivery is on the way' }),
      ).toBeVisible()
      await expect(freshnessSentence(recipientPhone.page)).toHaveText(/^Live location — updated/)
      await expect(courierMarker(recipientPhone.page)).toBeVisible()
      await expect(connectionSentence(recipientPhone.page)).toHaveText('Updating automatically')
    })

    await test.step('the Courier stops sharing and the marker goes at once', async () => {
      await stopSharing(courierPhone.page)

      // The one location change a page cannot work out for itself. Left to its own clock it would
      // keep this marker for up to two more minutes, so Stop has to arrive over the stream — and
      // this page is still connected, which is what makes that the reason it changed.
      await expect(courierMarker(recipientPhone.page)).toBeHidden()
      await expect(freshnessSentence(recipientPhone.page)).toHaveText(
        'The courier’s position is not available right now.',
      )
      await expect(connectionSentence(recipientPhone.page)).toHaveText('Updating automatically')
      // The Delivery is unchanged, and nothing about it was removed with the position.
      await expect(recipientPhone.page.getByText(deliveryReference)).toBeVisible()
      await expect(recipientPhone.page.getByText(COURIER.displayName)).toBeVisible()
    })

    await test.step('the Courier shares again', async () => {
      await startSharing(courierPhone.page)
      await expect(freshnessSentence(recipientPhone.page)).toHaveText(/^Live location — updated/)
      await expect(courierMarker(recipientPhone.page)).toBeVisible()
    })

    await test.step('the phone loses its network and the application restarts under it', async () => {
      // Offline first, so no reconnection can succeed while this journey is looking away; the
      // restart is what actually severs the stream the page already holds.
      await recipientPhone.context.setOffline(true)
      await restartApplication(baseURL as string)

      await expect(connectionSentence(recipientPhone.page)).toHaveText('Reconnecting for updates…')
      // A dropped stream is not a reason to throw away a perfectly good snapshot and its
      // timestamps, so everything the page was showing is still on screen.
      await expect(recipientPhone.page.getByText(deliveryReference)).toBeVisible()
      await expect(recipientPhone.page.getByText(HANDOFF.addressLabel)).toBeVisible()
      await expect(recipientPhone.page.getByText(COURIER.displayName)).toBeVisible()
      await expect(courierMarker(recipientPhone.page)).toBeVisible()
    })

    await test.step('the reading turns Delayed and then Unavailable with nothing arriving', async () => {
      await recipientPhone.page.clock.fastForward('00:45')
      await expect(freshnessSentence(recipientPhone.page)).toHaveText(/^Delayed location — updated/)
      // Still drawn: Delayed is old rather than unusable, and the product says so in words instead
      // of silently removing the only thing on screen that answers "where".
      await expect(courierMarker(recipientPhone.page)).toBeVisible()

      await recipientPhone.page.clock.fastForward('01:30')
      await expect(freshnessSentence(recipientPhone.page)).toHaveText(/^Location unavailable — last reported/)
      await expect(courierMarker(recipientPhone.page)).toBeHidden()
      // The handoff is an address rather than a measurement, so it does not expire with the reading.
      await expect(handoffMarker(recipientPhone.page)).toBeVisible()
      await expect(mapWithoutCourier(recipientPhone.page)).toBeVisible()

      // The whole point. The page is still cut off, so nothing was fetched and no hint arrived; the
      // marker went because this browser decided the reading had stopped meaning anything.
      await expect(connectionSentence(recipientPhone.page)).toHaveText('Reconnecting for updates…')
    })

    await test.step('the network returns and one snapshot catches the page up', async () => {
      await recipientPhone.context.setOffline(false)

      await expect(connectionSentence(recipientPhone.page)).toHaveText('Updating automatically')
      // Read from the server this time rather than aged locally. Coordinates only ever lived in the
      // restarted process's memory, so there is nothing to restore and the page says the same
      // sentence it says for a Courier who never started.
      await expect(freshnessSentence(recipientPhone.page)).toHaveText(
        'The courier’s position is not available right now.',
      )
      await expect(courierMarker(recipientPhone.page)).toBeHidden()
      await expect(recipientPhone.page.getByText(deliveryReference)).toBeVisible()
    })

    await test.step('a tampered link reveals nothing about any Delivery', async () => {
      // A fresh phone, because the one above holds a grant cookie for this Delivery and would be
      // answering from that rather than from the link being offered.
      const stranger = await openRecipientPhone(browser)
      try {
        // Everything this browser asks for. The bootstrap requests the Recipient application only
        // after a successful exchange, so a refused link should leave the map and the snapshot code
        // undownloaded — a fact about the network rather than a rule the page has to remember.
        const requested: string[] = []
        stranger.page.on('request', (request) => requested.push(new URL(request.url()).pathname))

        const link = await copyTrackingLink(dispatcher, delivery.id)
        await stranger.page.goto(tamperWith(link.url))

        await expect(
          stranger.page.getByText(
            'This tracking link is no longer available. Contact the delivery team that shared it.',
          ),
        ).toBeVisible()
        expect(requested).not.toContain('/track-app.js')
        expect(requested).not.toContain('/api/tracking/snapshot')
        // Unknown, malformed and expired all arrive as that one sentence, which is what stops the
        // page becoming a way to ask whether a Delivery exists. Expiry itself is proved against a
        // controlled server clock in TrackingLinkExpiryTest; there is no way to age a seven-day
        // link from a browser, and adding one would be the production backdoor this Issue rules out.
        const body = stranger.page.locator('body')
        await expect(body).not.toContainText(deliveryReference)
        await expect(body).not.toContainText(HANDOFF.addressLabel)
        await expect(body).not.toContainText(COURIER.displayName)
        await expect(stranger.page).toHaveURL((url) => url.hash === '')
      } finally {
        await stranger.context.close()
      }
    })
  } finally {
    await courierPhone.context.close()
    await recipientPhone.context.close()
  }
})

/**
 * Changes one character of the capability and leaves everything else — length, alphabet, the
 * version prefix — exactly as it was. A link that is obviously malformed would be refused by
 * whichever check runs first, which is not the refusal worth proving.
 */
function tamperWith(url: string): string {
  const [address, fragment] = url.split('#')
  const last = fragment.at(-1) ?? 'A'
  return `${address}#${fragment.slice(0, -1)}${last === 'A' ? 'B' : 'A'}`
}
