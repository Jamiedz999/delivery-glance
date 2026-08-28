export interface SystemStatus {
  application: string
  status: string
  /** Whether this deployment has proof of delivery configured; false hides capture entirely. */
  proofCaptureEnabled: boolean
  /**
   * Whether both seeded Internal Accounts still hold their factory password hashes. Only then may the
   * Sign-in page publish the demo credentials; false (or a failed probe) means it cannot supply them.
   */
  demoAccountsUnchanged: boolean
}

export async function fetchSystemStatus(): Promise<SystemStatus> {
  const response = await fetch('/api/system')

  if (!response.ok) {
    throw new Error(`System status request failed with ${response.status}`)
  }

  return (await response.json()) as SystemStatus
}
