import { ApiError, apiRequest } from './http'

export type InternalAccountRole = 'DISPATCHER' | 'COURIER'

export interface Session {
  displayName: string
  role: InternalAccountRole
}

export interface Credentials {
  email: string
  password: string
}

/** Resolves to `null` when nobody is signed in, which is an ordinary state rather than an error. */
export async function fetchSession(): Promise<Session | null> {
  try {
    return await apiRequest<Session>('/api/session')
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null
    }
    throw error
  }
}

export function signIn(credentials: Credentials): Promise<void> {
  return apiRequest<void>('/api/session/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ email: credentials.email, password: credentials.password }),
  })
}

export function signOut(): Promise<void> {
  return apiRequest<void>('/api/session', { method: 'DELETE' })
}
