import { type APIRequestContext, type PlaywrightWorkerArgs, expect } from '@playwright/test'
import type { Account } from './team'

/** The worker-scoped `playwright` fixture, which is where a fresh cookie jar comes from. */
export type PlaywrightFixture = PlaywrightWorkerArgs['playwright']

/**
 * A signed-in HTTP client for the two things a journey cannot do through a browser.
 *
 * The first is copying a Tracking Link. There is no control for it anywhere in the Dispatcher
 * workspace — `docs/planning/implementation/INCIDENTAL-FINDINGS.md` records why, and the README's
 * Recipient walkthrough has to describe a `curl` session for the same reason. Until that button
 * exists, a Recipient journey has to obtain its link the way a human currently does.
 *
 * The second is putting the Delivery Team back where a journey expects to find it. That is done
 * through the same endpoints the workspaces call and nothing else: this suite adds no reset route,
 * no test profile and no seeded fixture, because a backdoor that only exists for tests is a
 * backdoor that ships.
 */
export class TeamClient {
  private readonly context: APIRequestContext

  private constructor(context: APIRequestContext) {
    this.context = context
  }

  static async signIn(playwright: PlaywrightFixture, baseURL: string, account: Account): Promise<TeamClient> {
    const context = await playwright.request.newContext({ baseURL })
    const client = new TeamClient(context)

    // The public status route is what mints the CSRF cookie for a client that has none yet, exactly
    // as the first page load does for a browser.
    await client.get('/api/system')
    const response = await context.post('/api/session/login', {
      headers: await client.csrfHeader(),
      form: { email: account.email, password: account.password },
    })
    expect(response.status(), `sign-in for ${account.email}`).toBe(204)
    return client
  }

  async get<T>(path: string): Promise<T> {
    const response = await this.context.get(path)
    expect(response.ok(), `GET ${path} answered ${response.status()}`).toBe(true)
    return response.status() === 204 ? (undefined as T) : ((await response.json()) as T)
  }

  async post<T>(path: string, data?: unknown): Promise<T> {
    const response = await this.context.post(path, {
      headers: await this.csrfHeader(),
      ...(data === undefined ? {} : { data }),
    })
    expect(response.ok(), `POST ${path} answered ${response.status()}`).toBe(true)
    return response.status() === 204 ? (undefined as T) : ((await response.json()) as T)
  }

  async put<T>(path: string, data: unknown): Promise<T> {
    const response = await this.context.put(path, { headers: await this.csrfHeader(), data })
    expect(response.ok(), `PUT ${path} answered ${response.status()}`).toBe(true)
    return response.status() === 204 ? (undefined as T) : ((await response.json()) as T)
  }

  async delete(path: string): Promise<void> {
    const response = await this.context.delete(path, { headers: await this.csrfHeader() })
    expect(response.ok(), `DELETE ${path} answered ${response.status()}`).toBe(true)
  }

  async dispose(): Promise<void> {
    await this.context.dispose()
  }

  /** Read back from the jar on every call, because sign-in and sign-out both rotate the token. */
  private async csrfHeader(): Promise<Record<string, string>> {
    const { cookies } = await this.context.storageState()
    const token = cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')?.value
    return token === undefined ? {} : { 'X-XSRF-TOKEN': token }
  }
}
