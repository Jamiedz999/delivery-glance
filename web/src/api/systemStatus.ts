export interface SystemStatus {
  application: string
  status: string
}

export async function fetchSystemStatus(): Promise<SystemStatus> {
  const response = await fetch('/api/system')

  if (!response.ok) {
    throw new Error(`System status request failed with ${response.status}`)
  }

  return (await response.json()) as SystemStatus
}
