import clsx from 'clsx'
import { LayoutGrid, LogOut, Plus, Ticket } from 'lucide-react'
import type { ReactNode } from 'react'
import { NavLink, useNavigate } from 'react-router'
import { useMyCrews } from '../lib/crews'
import { useAuth } from '../providers/authContext'
import { Avatar, Logo } from './ui'

/**
 * 로그인한 화면의 바깥 틀.
 * - 휴대폰: 틀 없이 각 화면이 그대로 (화면마다 위쪽 헤더가 있다)
 * - 데스크톱(lg~): 왼쪽에 메뉴·내 크루 목록·계정이 있는 사이드바, 오른쪽에 넓은 본문
 */
export function AppShell({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-dvh">
      <Sidebar />
      <div className="lg:pl-72">
        <div className="lg:px-10 lg:py-8">{children}</div>
      </div>
    </div>
  )
}

function Sidebar() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const { data: crews } = useMyCrews()

  return (
    <aside className="fixed inset-y-0 left-0 z-20 hidden w-72 flex-col border-r border-stone-200 bg-white lg:flex">
      <div className="px-6 pt-7 pb-6">
        <NavLink to="/" aria-label="HabitCrew 홈">
          <Logo className="text-lg text-stone-900" />
        </NavLink>
      </div>

      <nav className="flex flex-col gap-1 px-3" aria-label="주 메뉴">
        <SideLink to="/" end icon={<LayoutGrid className="size-4" />}>
          홈
        </SideLink>
        <SideLink to="/crews/new" icon={<Plus className="size-4" />}>
          새 크루 만들기
        </SideLink>
        <SideLink to="/join" icon={<Ticket className="size-4" />}>
          초대 코드로 참여
        </SideLink>
      </nav>

      <div className="mt-8 min-h-0 flex-1 overflow-y-auto px-3">
        <p className="px-3 pb-2 text-xs font-semibold tracking-wide text-stone-400">내 크루</p>
        {crews && crews.length === 0 && <p className="px-3 text-sm text-stone-400">아직 참여한 크루가 없어요</p>}
        <ul className="flex flex-col gap-0.5">
          {crews?.map((crew) => (
            <li key={crew.id}>
              <NavLink
                to={`/crews/${crew.id}`}
                className={({ isActive }) =>
                  clsx(
                    'flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm transition',
                    isActive ? 'bg-brand-50 font-semibold text-brand-700' : 'text-stone-600 hover:bg-stone-50',
                  )
                }
              >
                <span
                  className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-stone-100 text-xs font-bold text-stone-500"
                  aria-hidden
                >
                  {crew.name.slice(0, 1)}
                </span>
                <span className="min-w-0 flex-1 truncate">{crew.name}</span>
                <span className="text-xs text-stone-400 tabular-nums">{crew.memberCount}명</span>
              </NavLink>
            </li>
          ))}
        </ul>
      </div>

      {user && (
        <div className="flex items-center gap-3 border-t border-stone-100 px-5 py-4">
          <Avatar userId={user.id} nickname={user.nickname} size="sm" />
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold">{user.nickname}</p>
            <p className="truncate text-xs text-stone-400">{user.guest ? '체험 계정' : user.email}</p>
          </div>
          <button
            onClick={async () => {
              await logout()
              navigate('/login')
            }}
            aria-label="로그아웃"
            title="로그아웃"
            className="flex size-9 items-center justify-center rounded-lg text-stone-400 hover:bg-stone-100 hover:text-stone-700"
          >
            <LogOut className="size-4" />
          </button>
        </div>
      )}
    </aside>
  )
}

function SideLink({ to, end, icon, children }: { to: string; end?: boolean; icon: ReactNode; children: ReactNode }) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        clsx(
          'flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition',
          isActive ? 'bg-stone-900 text-white' : 'text-stone-700 hover:bg-stone-100',
        )
      }
    >
      <span aria-hidden>{icon}</span>
      {children}
    </NavLink>
  )
}
