export interface SystemStatus {
  application: string
  status: string
  /** Whether this deployment has proof of delivery configured; false hides capture entirely. */
  proofCaptureEnabled: boolean
}

export async function fetchSystemStatus(): Promise<SystemStatus> {
  const response = await fetch('/api/system')

  if (!response.ok) {
    throw new Error(`System status request failed with ${response.status}`)
  }

  return (await response.json()) as SystemStatus
}
