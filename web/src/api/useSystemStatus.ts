import { useQuery } from '@tanstack/react-query'
import { fetchSystemStatus } from './systemStatus'

export function useSystemStatus() {
  return useQuery({
    queryKey: ['system-status'],
    queryFn: fetchSystemStatus,
  })
}
