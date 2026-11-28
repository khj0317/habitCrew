import clsx from 'clsx'
import { LayoutGrid, LogOut, Plus, Settings, Ticket } from 'lucide-react'
import type { ReactNode } from 'react'
import { NavLink, useNavigate } from 'react-router'
import { useMyCrews } from '../lib/crews'
import { useAuth } from '../providers/authContext'
import { Avatar, Logo } from './ui'

/**
 * 로그인한 화면의 바깥 틀.
 * - 휴대폰: 틀 없이 각 화면이 그대로 (화면마다 위쪽 헤더가 있다)
 * - 데스크톱(lg~): 위쪽 가로 메뉴(로고·메뉴·계정)와 내 크루 바로가기 줄, 그 아래 넓은 본문
 */
export function AppShell({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-dvh">
      <TopNav />
      <div className="lg:mx-auto lg:max-w-6xl lg:px-10 lg:py-8">{children}</div>
    </div>
  )
}

function TopNav() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const { data: crews } = useMyCrews()

  return (
    <header className="sticky top-0 z-20 hidden border-b-2 border-ink bg-white lg:block">
      <div className="mx-auto flex h-16 max-w-6xl items-center gap-8 px-10">
        <NavLink to="/" aria-label="HabitCrew 홈">
          <Logo className="text-lg text-ink" />
        </NavLink>

        <nav className="flex items-center gap-1" aria-label="주 메뉴">
          <TopLink to="/" end icon={<LayoutGrid className="size-4" />}>
            홈
          </TopLink>
          <TopLink to="/crews/new" icon={<Plus className="size-4" />}>
            새 크루
          </TopLink>
          <TopLink to="/join" icon={<Ticket className="size-4" />}>
            초대 코드
          </TopLink>
          <TopLink to="/settings" icon={<Settings className="size-4" />}>
            설정
          </TopLink>
        </nav>

        {user && (
          <div className="ml-auto flex items-center gap-3">
            <Avatar userId={user.id} nickname={user.nickname} size="sm" />
            <div className="leading-tight">
              <p className="text-sm font-extrabold">{user.nickname}</p>
              <p className="text-xs text-stone-500">{user.guest ? '체험 계정' : user.email}</p>
            </div>
            <button
              onClick={async () => {
                await logout()
                navigate('/login')
              }}
              aria-label="로그아웃"
              title="로그아웃"
              className="ml-1 flex size-9 items-center justify-center rounded-lg text-stone-500 hover:bg-stone-100 hover:text-ink"
            >
              <LogOut className="size-4" />
            </button>
          </div>
        )}
      </div>

      {/* 내 크루 바로가기: 이름표처럼 한 줄로 */}
      {crews && crews.length > 0 && (
        <div className="border-t border-stone-200 bg-stone-50">
          <ul className="mx-auto flex max-w-6xl items-center gap-2 overflow-x-auto px-10 py-2" aria-label="내 크루">
            <li className="shrink-0 pr-1 text-xs font-bold text-stone-500">내 크루</li>
            {crews.map((crew) => (
              <li key={crew.id} className="shrink-0">
                <NavLink
                  to={`/crews/${crew.id}`}
                  className={({ isActive }) =>
                    clsx(
                      'flex items-center gap-1.5 rounded-md border-2 px-2.5 py-1 text-xs font-bold transition',
                      isActive
                        ? 'border-ink bg-lime-400 text-ink shadow-hard-sm'
                        : 'border-stone-300 bg-white text-stone-600 hover:border-ink',
                    )
                  }
                >
                  {crew.name}
                  <span className="font-medium text-stone-500">{crew.memberCount}명</span>
                </NavLink>
              </li>
            ))}
          </ul>
        </div>
      )}
    </header>
  )
}

function TopLink({ to, end, icon, children }: { to: string; end?: boolean; icon: ReactNode; children: ReactNode }) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        clsx(
          'flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-bold transition',
          isActive ? 'bg-ink text-lime-400' : 'text-stone-600 hover:bg-stone-100 hover:text-ink',
        )
      }
    >
      <span aria-hidden>{icon}</span>
      {children}
    </NavLink>
  )
}
