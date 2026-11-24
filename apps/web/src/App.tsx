import type { ReactNode } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router'
import { FullScreenSpinner } from './components/ui'
import { LoginPage, SignupPage } from './pages/AuthPages'
import { CertificationPage } from './pages/CertificationPage'
import { CertifyPage } from './pages/CertifyPage'
import { CreateCrewPage, EditCrewPage } from './pages/CrewFormPages'
import { CrewDetailPage } from './pages/CrewDetailPage'
import { HomePage } from './pages/HomePage'
import { JoinCrewPage } from './pages/JoinCrewPage'
import { SettlementsPage } from './pages/SettlementsPage'
import { useAuth } from './providers/authContext'
import { useRedirectTarget } from './lib/redirect'

/** 로그인하지 않았으면 로그인 화면으로 보내고, 로그인 후 원래 주소(예: 초대 링크)로 돌아오게 한다 */
function RequireAuth({ children }: { children: ReactNode }) {
  const { user, restoring } = useAuth()
  const location = useLocation()
  if (restoring) return <FullScreenSpinner />
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  return children
}

/**
 * 로그인 화면은 로그인하지 않은 사람만 본다.
 * 로그인에 성공하는 순간 여기서 먼저 다른 곳으로 보내므로, 원래 가려던 주소(초대 링크 등)로 보내야 한다.
 */
function GuestOnly({ children }: { children: ReactNode }) {
  const { user, restoring } = useAuth()
  const target = useRedirectTarget()
  if (restoring) return <FullScreenSpinner />
  if (user) return <Navigate to={target} replace />
  return children
}

const authed = (page: ReactNode) => <RequireAuth>{page}</RequireAuth>
const guest = (page: ReactNode) => <GuestOnly>{page}</GuestOnly>

export function App() {
  return (
    <Routes>
      <Route path="/" element={authed(<HomePage />)} />
      <Route path="/crews/new" element={authed(<CreateCrewPage />)} />
      <Route path="/crews/:crewId" element={authed(<CrewDetailPage />)} />
      <Route path="/crews/:crewId/edit" element={authed(<EditCrewPage />)} />
      <Route path="/crews/:crewId/certify" element={authed(<CertifyPage />)} />
      <Route path="/crews/:crewId/settlements" element={authed(<SettlementsPage />)} />
      <Route path="/crews/:crewId/certifications/:certificationId" element={authed(<CertificationPage />)} />
      <Route path="/join" element={authed(<JoinCrewPage />)} />
      <Route path="/join/:code" element={authed(<JoinCrewPage />)} />
      <Route path="/login" element={guest(<LoginPage />)} />
      <Route path="/signup" element={guest(<SignupPage />)} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
