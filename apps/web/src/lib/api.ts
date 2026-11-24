// API 클라이언트
// - 액세스 토큰은 메모리에만 둔다 (localStorage에 두면 XSS로 탈취될 수 있음)
// - 리프레시 토큰은 서버가 HttpOnly 쿠키로 관리하고, 401이 나면 한 번 재발급을 시도한 뒤 원래 요청을 다시 보낸다
// - 여러 요청이 동시에 401을 받아도 재발급은 한 번만 일어나게 Promise를 공유한다

import type { TokenResponse } from './types'

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: Record<string, string>

  constructor(status: number, code: string, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

let accessToken: string | null = null
let refreshing: Promise<TokenResponse | null> | null = null
let onSessionExpired: (() => void) | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function setSessionExpiredHandler(handler: () => void) {
  onSessionExpired = handler
}

async function requestRefresh(): Promise<TokenResponse | null> {
  const res = await fetch('/api/auth/refresh', { method: 'POST', credentials: 'same-origin' })
  if (!res.ok) return null
  const body = (await res.json()) as TokenResponse
  accessToken = body.accessToken
  return body
}

/**
 * 쿠키의 리프레시 토큰으로 새 토큰을 받는다. 로그인 상태가 아니면 null.
 * - 같은 탭에서 여러 요청이 동시에 부르면 한 번만 보낸다 (Promise 공유)
 * - 다른 탭과 동시에 보내 한쪽이 실패하면, 다른 탭이 받은 새 쿠키로 한 번 더 시도한다
 */
export function refreshSession(): Promise<TokenResponse | null> {
  refreshing ??= requestRefresh()
    .then((first) => first ?? new Promise<void>((r) => setTimeout(r, 300)).then(requestRefresh))
    .catch(() => null)
    .finally(() => {
      refreshing = null
    })
  return refreshing
}

type Options = { method?: string; body?: unknown; auth?: boolean }

export async function api<T>(path: string, { method = 'GET', body, auth = true }: Options = {}): Promise<T> {
  // 사진 업로드(FormData)는 브라우저가 boundary를 넣은 Content-Type을 직접 정하게 둔다
  const isForm = body instanceof FormData
  const send = () => {
    const headers: Record<string, string> = {}
    if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json'
    if (auth && accessToken) headers.Authorization = `Bearer ${accessToken}`
    return fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : isForm ? body : JSON.stringify(body),
      credentials: 'same-origin',
    })
  }

  let res: Response
  try {
    res = await send()
  } catch {
    throw new ApiError(0, 'NETWORK', '서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.')
  }

  if (res.status === 401 && auth) {
    const renewed = await refreshSession()
    if (renewed) {
      res = await send()
    } else {
      accessToken = null
      onSessionExpired?.()
    }
  }

  if (!res.ok) throw await toError(res)
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

async function toError(res: Response): Promise<ApiError> {
  try {
    const data = await res.json()
    const fieldErrors: Record<string, string> = {}
    for (const e of data.errors ?? []) {
      fieldErrors[e.field] ??= e.reason
    }
    return new ApiError(res.status, data.code ?? 'UNKNOWN', data.message ?? '요청을 처리하지 못했어요.', fieldErrors)
  } catch {
    return new ApiError(res.status, 'UNKNOWN', '요청을 처리하지 못했어요.')
  }
}
