import { useSystemStatus } from '../api/useSystemStatus'

export function ConnectionPage() {
  const { data, isPending, isError } = useSystemStatus()

  if (isPending) {
    return <p role="status">Connecting to the Delivery Glance API…</p>
  }

  if (isError) {
    return <p role="alert">Could not reach the Delivery Glance API.</p>
  }

  return (
    <p role="status">
      Frontend connected to Delivery Glance API — status: {data.status}
    </p>
  )
}
