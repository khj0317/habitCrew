import { CalendarDays, Crown, LogOut, Plus, Ticket, Users } from 'lucide-react'
import { Link, useNavigate } from 'react-router'
import { GuestBanner } from '../components/GuestBanner'
import { PushCard } from '../components/PushCard'
import { Button, ErrorState, Logo } from '../components/ui'
import { useMyCrews } from '../lib/crews'
import { formatDays, formatPoints } from '../lib/format'
import type { CrewSummary } from '../lib/types'
import { useAuth } from '../providers/authContext'

export function HomePage() {
  const { user, logout } = useAuth()
  const { data: crews, isPending, isError, refetch } = useMyCrews()

  return (
    <div className="page-wide flex flex-col">
      <header className="flex items-center justify-between px-5 py-4 lg:hidden">
        <Logo className="text-stone-900" />
        <button
          onClick={logout}
          className="inline-flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm text-stone-500 hover:bg-stone-100"
        >
          <LogOut className="size-4" aria-hidden />
          로그아웃
        </button>
      </header>

      <main className="flex flex-1 flex-col px-5 pb-10 lg:px-0">
        <section className="mt-4 lg:mt-0">
          <p className="text-stone-500">안녕하세요,</p>
          <h1 className="text-2xl font-bold lg:text-3xl">{user?.nickname}님 🙌</h1>
        </section>

        <div className="mt-6 grid gap-3 lg:grid-cols-2 lg:items-start">
          <GuestBanner />
          <PushCard />
        </div>

        {isPending ? (
          <div className="mt-8 grid gap-3 lg:grid-cols-2 xl:grid-cols-3" aria-label="불러오는 중">
            {[0, 1].map((i) => (
              <div key={i} className="h-32 animate-pulse rounded-2xl bg-stone-200/60" />
            ))}
          </div>
        ) : isError ? (
          <ErrorState
            message="크루 목록을 불러오지 못했어요."
            action={
              <Button variant="secondary" onClick={() => refetch()}>
                다시 시도
              </Button>
            }
          />
        ) : crews.length === 0 ? (
          <EmptyCrews />
        ) : (
          <>
            <div className="mt-8 mb-3 flex items-center justify-between">
              <h2 className="font-semibold text-stone-700">
                내 크루 <span className="text-brand-600">{crews.length}</span>
              </h2>
              <Link to="/join" className="text-sm font-medium text-stone-500 hover:text-stone-800">
                초대 코드 입력
              </Link>
            </div>
            {/* 휴대폰은 한 줄, 데스크톱은 2~3칸 카드 */}
            <ul className="grid gap-3 lg:grid-cols-2 lg:gap-4 xl:grid-cols-3">
              {crews.map((crew) => (
                <li key={crew.id}>
                  <CrewCard crew={crew} />
                </li>
              ))}
              <li>
                <Link
                  to="/crews/new"
                  className="flex h-14 items-center justify-center gap-1.5 rounded-2xl border border-dashed border-stone-300 text-sm font-medium text-stone-500 transition hover:border-brand-500 hover:text-brand-700 lg:h-full lg:min-h-36"
                >
                  <Plus className="size-4" aria-hidden />새 크루 만들기
                </Link>
              </li>
            </ul>
          </>
        )}
      </main>
    </div>
  )
}

function CrewCard({ crew }: { crew: CrewSummary }) {
  return (
    <Link
      to={`/crews/${crew.id}`}
      className="block h-full rounded-2xl border border-stone-200 bg-white p-5 transition hover:border-brand-500 hover:shadow-sm"
    >
      <div className="flex items-start justify-between gap-3">
        <h3 className="text-[17px] font-bold leading-snug">{crew.name}</h3>
        {crew.myRole === 'OWNER' && (
          <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-700">
            <Crown className="size-3" aria-hidden />
            크루장
          </span>
        )}
      </div>
      {crew.description && <p className="mt-1 line-clamp-1 text-sm text-stone-500">{crew.description}</p>}
      <div className="mt-4 flex flex-wrap gap-x-4 gap-y-1 text-[13px] text-stone-600">
        <span className="inline-flex items-center gap-1">
          <CalendarDays className="size-3.5 text-stone-400" aria-hidden />
          {formatDays(crew.certDays)}
        </span>
        <span className="inline-flex items-center gap-1">
          <Users className="size-3.5 text-stone-400" aria-hidden />
          {crew.memberCount}/{crew.maxMembers}명
        </span>
        <span>벌금 {formatPoints(crew.penaltyPoints)}</span>
      </div>
    </Link>
  )
}

function EmptyCrews() {
  const navigate = useNavigate()
  return (
    <section className="mt-8 flex flex-1 flex-col items-center justify-center rounded-3xl border border-dashed border-stone-300 bg-white px-6 py-14 text-center lg:flex-none lg:py-20">
      <div className="flex size-14 items-center justify-center rounded-2xl bg-brand-50 text-3xl">🌱</div>
      <h2 className="mt-5 text-lg font-semibold">아직 참여한 크루가 없어요</h2>
      <p className="mt-2 text-sm leading-relaxed text-stone-500">
        크루를 직접 만들거나, 친구에게 받은
        <br />
        초대 코드로 참여해 보세요.
      </p>
      <div className="mt-8 flex w-full flex-col gap-2 lg:max-w-xs">
        <Button onClick={() => navigate('/crews/new')}>
          <Plus className="size-4" aria-hidden />
          크루 만들기
        </Button>
        <Button variant="ghost" onClick={() => navigate('/join')}>
          <Ticket className="size-4" aria-hidden />
          초대 코드로 참여
        </Button>
      </div>
    </section>
  )
}
