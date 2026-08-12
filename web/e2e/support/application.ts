import { execFile } from 'node:child_process'
import { resolve } from 'node:path'
import { promisify } from 'node:util'

const run = promisify(execFile)

const REPOSITORY_ROOT = resolve(import.meta.dirname, '../../..')

/**
 * Restarts the application, which is how a journey takes a live stream away.
 *
 * There is no lighter way to do it. A browser told it is offline stops making new requests but
 * leaves an established stream open — Chromium's emulation gates connections rather than severing
 * them — so a page that had already connected would go on believing it was connected. Restarting is
 * the real event, and it is one the README already describes as the whole recovery procedure: every
 * connection and every hint version goes, the pages reconnect, and they reread.
 *
 * Coordinates go with it, because they only ever lived in this process's memory. That is the
 * product working, not the test cheating around it.
 */
export async function restartApplication(baseURL: string): Promise<void> {
  await run('docker', ['compose', 'restart', 'app'], { cwd: REPOSITORY_ROOT })
  await waitUntilHealthy(baseURL)
}

async function waitUntilHealthy(baseURL: string, timeoutMs = 60_000): Promise<void> {
  const health = new URL('/actuator/health', baseURL)
  const deadline = Date.now() + timeoutMs

  while (Date.now() < deadline) {
    try {
      if ((await fetch(health)).ok) {
        return
      }
    } catch {
      // Not up yet; the deadline above is what decides when that stops being acceptable.
    }
    await new Promise((wake) => setTimeout(wake, 500))
  }
  throw new Error(`The application did not become healthy again within ${timeoutMs}ms of a restart.`)
}
