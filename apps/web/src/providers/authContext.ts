import { createContext, useContext } from 'react'
import type { User } from '../lib/types'

export type AuthState = {
  user: User | null
  /** 새로고침 직후 쿠키로 로그인 상태를 복구하는 중 */
  restoring: boolean
  login: (email: string, password: string) => Promise<void>
  signup: (email: string, password: string, nickname: string) => Promise<void>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth는 AuthProvider 안에서만 쓸 수 있어요.')
  return value
}
