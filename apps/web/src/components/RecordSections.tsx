import clsx from 'clsx'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import type { ReactNode } from 'react'
import { currentMonth, formatMonth, shiftMonth, useCalendar, useRanking } from '../lib/records'
import type { DayStatus } from '../lib/types'
import { Avatar } from './ui'

export function MonthSwitcher({ month, onChange }: { month: string; onChange: (month: string) => void }) {
  const isCurrent = month >= currentMonth()
  return (
    <div className="flex items-center justify-center gap-2">
      <button
        onClick={() => onChange(shiftMonth(month, -1))}
        aria-label="이전 달"
        className="flex size-9 items-center justify-center rounded-lg border-2 border-ink bg-white text-ink shadow-hard-sm active:translate-x-[2px] active:translate-y-[2px] active:shadow-none"
      >
        <ChevronLeft className="size-5" />
      </button>
      <span className="min-w-28 text-center text-lg font-black tabular-nums" aria-live="polite">
        {formatMonth(month)}
      </span>
      <button
        onClick={() => onChange(shiftMonth(month, 1))}
        aria-label="다음 달"
        disabled={isCurrent}
        className="flex size-9 items-center justify-center rounded-lg border-2 border-ink bg-white text-ink shadow-hard-sm active:translate-x-[2px] active:translate-y-[2px] active:shadow-none disabled:opacity-30 disabled:shadow-none"
      >
        <ChevronRight className="size-5" />
      </button>
    </div>
  )
}

const MEDALS = ['🥇', '🥈', '🥉']

/** 이번 달 인증 횟수 랭킹 */
export function RankingCard({ crewId, month, myUserId }: { crewId: number; month: string; myUserId: number }) {
  const { data, isPending } = useRanking(crewId, month)
  const top = data?.entries[0]?.count ?? 0

  return (
    <Card title="월간 랭킹" subtitle="이번 달 인증을 가장 많이 한 순서예요">
      {isPending ? (
        <div className="h-32 animate-pulse rounded-xl bg-stone-100" />
      ) : (
        <ol className="flex flex-col gap-2.5">
          {data!.entries.map((e) => (
            <li key={e.userId} className="flex items-center gap-3">
              <span className="w-6 text-center text-sm font-bold text-stone-400 tabular-nums" aria-label={`${e.rank}위`}>
                {e.count > 0 && e.rank <= 3 ? MEDALS[e.rank - 1] : e.rank}
              </span>
              <Avatar userId={e.userId} nickname={e.nickname} size="sm" />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium">
                  {e.nickname}
                  {e.userId === myUserId && <span className="ml-1 text-xs text-stone-400">(나)</span>}
                </p>
                <div className="mt-1 h-2.5 overflow-hidden rounded-sm border-2 border-ink bg-white">
                  <div
                    className="h-full bg-brand-500"
                    style={{ width: `${top === 0 ? 0 : (e.count / top) * 100}%` }}
                  />
                </div>
              </div>
              <span className="w-10 text-right text-sm font-extrabold tabular-nums">{e.count}회</span>
            </li>
          ))}
        </ol>
      )}
    </Card>
  )
}

const STATUS_STYLE: Record<DayStatus, { cell: string; label: string }> = {
  CERTIFIED: { cell: 'border-2 border-ink bg-lime-400 text-ink shadow-hard-sm', label: '인증함' },
  MISSED: { cell: 'border-2 border-coral-400 bg-coral-100 text-coral-600', label: '빼먹음' },
  REST: { cell: 'bg-stone-100 text-stone-300', label: '쉬는 날' },
  PENDING: { cell: 'border-2 border-dashed border-ink bg-white text-ink', label: '오늘 (아직)' },
  FUTURE: { cell: 'text-stone-300', label: '' },
  NOT_JOINED: { cell: 'text-stone-200', label: '참여 전' },
}

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

/** 한 달 인증 달력 (잔디). 크루원을 골라 다른 사람의 달력도 볼 수 있다 */
export function CalendarCard({
  crewId,
  month,
  members,
  selectedUserId,
  onSelectUser,
}: {
  crewId: number
  month: string
  members: { userId: number; nickname: string }[]
  selectedUserId: number
  onSelectUser: (userId: number) => void
}) {
  const { data, isPending } = useCalendar(crewId, month, selectedUserId)
  const [y, m] = month.split('-').map(Number)
  const leadingBlanks = new Date(y, m - 1, 1).getDay()

  return (
    <Card
      title="인증 달력"
      subtitle={data ? `${data.nickname}님 · 인증 ${data.certifiedCount}회 · 빼먹음 ${data.missedCount}회` : ' '}
    >
      {members.length > 1 && (
        <div className="-mx-1 mb-4 flex gap-1.5 overflow-x-auto px-1 pb-1" role="tablist" aria-label="크루원 고르기">
          {members.map((member) => (
            <button
              key={member.userId}
              role="tab"
              aria-selected={member.userId === selectedUserId}
              onClick={() => onSelectUser(member.userId)}
              className={clsx(
                'shrink-0 rounded-md border-2 px-3 py-1.5 text-xs font-bold transition',
                member.userId === selectedUserId
                  ? 'border-ink bg-ink text-lime-400'
                  : 'border-stone-300 bg-white text-stone-600 hover:border-ink',
              )}
            >
              {member.nickname}
            </button>
          ))}
        </div>
      )}
      <div className="grid grid-cols-7 gap-1 text-center" role="grid" aria-label={`${formatMonth(month)} 인증 달력`}>
        {WEEKDAYS.map((d, i) => (
          <div
            key={d}
            className={clsx('pb-1 text-xs', i === 0 ? 'text-red-400' : i === 6 ? 'text-sky-500' : 'text-stone-400')}
          >
            {d}
          </div>
        ))}
        {Array.from({ length: leadingBlanks }, (_, i) => (
          <div key={`blank-${i}`} />
        ))}
        {isPending
          ? Array.from({ length: 30 }, (_, i) => <div key={i} className="aspect-square animate-pulse rounded-lg bg-stone-100" />)
          : data!.days.map((day) => {
              const style = STATUS_STYLE[day.status]
              const dayNumber = Number(day.date.slice(8))
              return (
                <div
                  key={day.date}
                  role="gridcell"
                  title={style.label ? `${m}월 ${dayNumber}일 · ${style.label}` : undefined}
                  aria-label={`${m}월 ${dayNumber}일 ${style.label}`}
                  className={clsx(
                    'flex aspect-square items-center justify-center rounded-md text-xs font-bold tabular-nums',
                    style.cell,
                  )}
                >
                  {dayNumber}
                </div>
              )
            })}
      </div>
      <ul className="mt-4 flex flex-wrap gap-x-4 gap-y-1.5 text-xs text-stone-500">
        {(['CERTIFIED', 'MISSED', 'REST', 'PENDING'] as DayStatus[]).map((status) => (
          <li key={status} className="inline-flex items-center gap-1.5">
            <span className={clsx('size-3 rounded', STATUS_STYLE[status].cell)} aria-hidden />
            {STATUS_STYLE[status].label}
          </li>
        ))}
      </ul>
    </Card>
  )
}

function Card({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <section className="card p-5">
      <h2 className="text-lg font-black">{title}</h2>
      <p className="mt-0.5 mb-4 text-xs text-stone-500">{subtitle}</p>
      {children}
    </section>
  )
}
