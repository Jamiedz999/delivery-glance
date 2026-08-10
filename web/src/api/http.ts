const UNSAFE_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])

/** Matches the cookie and header names Spring Security's CookieCsrfTokenRepository uses. */
const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'

export interface FieldMessage {
  field: string
  message: string
}

/** The RFC 9457 problem the API returns, plus the stable `code` this app branches on. */
export interface ApiProblem {
  status: number
  code: string
  title?: string
  detail?: string
  errors?: FieldMessage[]
  currentState?: string
  currentVersion?: number
}

export class ApiError extends Error {
  readonly problem: ApiProblem

  constructor(problem: ApiProblem) {
    super(problem.detail ?? problem.title ?? `Request failed with ${problem.status}`)
    this.name = 'ApiError'
    this.problem = problem
  }

  get status(): number {
    return this.problem.status
  }

  get code(): string {
    return this.problem.code
  }

  /** Server-side validation messages keyed by the field path they belong to. */
  fieldMessages(): Record<string, string> {
    const messages: Record<string, string> = {}
    for (const error of this.problem.errors ?? []) {
      messages[error.field] ??= error.message
    }
    return messages
  }
}

function readCookie(name: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((entry) => entry.startsWith(`${name}=`))
    ?.slice(name.length + 1)
}

async function readProblem(response: Response): Promise<ApiProblem> {
  try {
    const body = (await response.json()) as Partial<ApiProblem>
    return { ...body, status: response.status, code: body.code ?? 'unknown-error' }
  } catch {
    return { status: response.status, code: 'unknown-error' }
  }
}

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)

  if (UNSAFE_METHODS.has(method)) {
    // The browser reads the CSRF cookie the server set and echoes it back as a header.
    const token = readCookie(CSRF_COOKIE)
    if (token !== undefined) {
      headers.set(CSRF_HEADER, token)
    }
  }

  const response = await fetch(path, { ...init, method, headers, credentials: 'same-origin' })

  if (!response.ok) {
    throw new ApiError(await readProblem(response))
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}
