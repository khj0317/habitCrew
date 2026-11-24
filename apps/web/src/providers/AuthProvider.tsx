import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, refreshSession, setAccessToken, setSessionExpiredHandler } from '../lib/api'
import type { TokenResponse, User } from '../lib/types'
import { AuthContext, type AuthState } from './authContext'

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [user, setUser] = useState<User | null>(null)
  const [restoring, setRestoring] = useState(true)

  // 새로고침해도 HttpOnly 쿠키의 리프레시 토큰으로 로그인 상태를 되살린다
  useEffect(() => {
    let cancelled = false
    refreshSession().then((session) => {
      if (cancelled) return
      setUser(session?.user ?? null)
      setRestoring(false)
    })
    setSessionExpiredHandler(() => {
      setUser(null)
      queryClient.clear()
    })
    return () => {
      cancelled = true
    }
  }, [queryClient])

  const applySession = useCallback((session: TokenResponse) => {
    setAccessToken(session.accessToken)
    setUser(session.user)
  }, [])

  const login = useCallback<AuthState['login']>(
    async (email, password) => {
      applySession(await api<TokenResponse>('/api/auth/login', { method: 'POST', body: { email, password }, auth: false }))
    },
    [applySession],
  )

  const signup = useCallback<AuthState['signup']>(
    async (email, password, nickname) => {
      applySession(
        await api<TokenResponse>('/api/auth/signup', {
          method: 'POST',
          body: { email, password, nickname },
          auth: false,
        }),
      )
    },
    [applySession],
  )

  const logout = useCallback(async () => {
    try {
      await api<void>('/api/auth/logout', { method: 'POST', auth: false })
    } finally {
      setAccessToken(null)
      setUser(null)
      queryClient.clear()
    }
  }, [queryClient])

  const value = useMemo(() => ({ user, restoring, login, signup, logout }), [user, restoring, login, signup, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
