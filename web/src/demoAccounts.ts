/**
 * The two pre-provisioned Internal Accounts, in one place so the code that shows them and the code
 * that signs in with them cannot drift apart. There is no third account and no registration.
 *
 * These are the credentials the first Flyway migration seeds with its default placeholders. The
 * Sign-in page fills its form from here, but publishes them only while the server confirms the
 * seeded rows still hold them — see `demoAccountsUnchanged` on `/api/system`. Two hand-kept copies
 * would drift, and the drift would land as a button that fills a password which no longer signs in.
 *
 * `README.md` keeps its own copy: that is prose for a human, and a stale line there is a stale doc,
 * not a lying page.
 */
export interface DemoAccount {
  /** The role, shown on the Sign-in page and used to name that account's fill button. */
  role: string
  email: string
  password: string
}

export const DISPATCHER_ACCOUNT: DemoAccount = {
  role: 'Dispatcher',
  email: 'dispatcher@delivery-glance.example',
  password: 'Dispatcher-Demo-2026!',
}

export const COURIER_ACCOUNT: DemoAccount = {
  role: 'Courier',
  email: 'courier@delivery-glance.example',
  password: 'Courier-Demo-2026!',
}

export const DEMO_ACCOUNTS: DemoAccount[] = [DISPATCHER_ACCOUNT, COURIER_ACCOUNT]
