import { createContext, useContext } from 'react'
import type { TokenResponse, User } from '../lib/types'

export type AuthState = {
  user: User | null
  /** 새로고침 직후 쿠키로 로그인 상태를 복구하는 중 */
  restoring: boolean
  login: (email: string, password: string) => Promise<void>
  signup: (email: string, password: string, nickname: string) => Promise<void>
  /** 로그인 없이 둘러보기: 체험 계정을 만들어 바로 로그인한다 */
  startGuest: () => Promise<void>
  /** 닉네임·알림 설정을 바꾼 뒤 화면의 사용자 정보를 바꾼다 */
  updateUser: (user: User) => void
  /** 비밀번호를 바꾸면 서버가 새 토큰을 준다 */
  applySession: (session: TokenResponse) => void
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth는 AuthProvider 안에서만 쓸 수 있어요.')
  return value
}
