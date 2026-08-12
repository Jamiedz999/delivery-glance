/**
 * The fictional Delivery Team the journeys are about, and the fictional place it works in.
 *
 * Every value here is invented. That matters twice over: a Courier coordinate is the one thing this
 * product promises never to keep, so a test fixture must not be a real person's doorstep, and a
 * reader of a public portfolio repository should be able to tell at a glance that nothing here is
 * anybody's address.
 */

/** One of the two pre-provisioned Internal Accounts. There is no third, and no registration. */
export interface Account {
  email: string
  password: string
  displayName: string
}

/**
 * The seeded defaults, overridable for a deployment whose first Flyway run used different `DEMO_*`
 * values. A journey never invents an account: these two are the whole of the Delivery Team.
 */
export const DISPATCHER: Account = {
  email: process.env.E2E_DISPATCHER_EMAIL ?? 'dispatcher@delivery-glance.example',
  password: process.env.E2E_DISPATCHER_PASSWORD ?? 'Dispatcher-Demo-2026!',
  displayName: process.env.E2E_DISPATCHER_DISPLAY_NAME ?? 'Dana the Dispatcher',
}

export const COURIER: Account = {
  email: process.env.E2E_COURIER_EMAIL ?? 'courier@delivery-glance.example',
  password: process.env.E2E_COURIER_PASSWORD ?? 'Courier-Demo-2026!',
  displayName: process.env.E2E_COURIER_DISPLAY_NAME ?? 'Cory the Courier',
}

export interface Position {
  latitude: number
  longitude: number
}

/**
 * A pickup, a handoff, and two places the Courier stands.
 *
 * `COURIER_AT_PICKUP` is within a few hundred metres of `PICKUP` so the Courier is genuinely the
 * nearest candidate, and `COURIER_EN_ROUTE` is far enough away that a moved marker is a visible
 * change rather than a rounding difference. Nothing depends on the absolute values, only on the
 * distances between them.
 */
export const PICKUP = {
  addressLabel: 'Glance Depot, 1 Fictional Way',
  latitude: 51.5,
  longitude: -0.12,
}

export const HANDOFF = {
  addressLabel: '14 Invented Crescent, Apartment 3',
  latitude: 51.51,
  longitude: -0.13,
}

export const COURIER_AT_PICKUP: Position = { latitude: 51.5005, longitude: -0.1205 }

export const COURIER_EN_ROUTE: Position = { latitude: 51.5042, longitude: -0.1241 }

/**
 * Reported by the emulated device with every position. Comfortably inside the hundred metres the
 * server treats as usable, so an accepted report is accepted for the reason the journey is about
 * rather than incidentally passing the accuracy gate.
 */
export const REPORTED_ACCURACY_METRES = 12

/**
 * One run's Delivery References, unique across runs and knowable in advance within one.
 *
 * A Reference is unique for the lifetime of the database, and the Compose volume outlives a test
 * run, so a hard-coded string would pass once and then fail forever. What a journey actually needs
 * is not a fixed string but a *known* one: this way a test asserts on the Delivery it created by
 * name, instead of picking the newest row and hoping.
 */
const RUN = new Date()
  .toISOString()
  .replaceAll(/[^0-9]/g, '')
  .slice(0, 14)

export function reference(journey: string): string {
  return `DG-E2E-${RUN}-${journey}`
}
