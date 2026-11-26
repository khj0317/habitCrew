import { ChevronRight } from 'lucide-react'
import { Link } from 'react-router'
import { formatPoints } from '../lib/format'
import { useCrewStats } from '../lib/settlements'

/** 내 기록 요약: 연속 인증 · 총 인증 · 쌓인 벌금, 그리고 정산 내역으로 가는 길 */
export function MyStatsCard({ crewId }: { crewId: number }) {
  const { data, isPending, isError } = useCrewStats(crewId)
  if (isPending) return <div className="h-24 animate-pulse rounded-3xl bg-stone-200/60" />
  if (isError) return null
  const me = data.me

  return (
    <section className="rounded-3xl border border-stone-200 bg-white">
      <h2 className="sr-only">내 기록</h2>
      <dl className="grid grid-cols-3 divide-x divide-stone-100 py-4 text-center">
        <Stat label="연속 인증" value={`${me.currentStreak}일`} emoji={me.currentStreak >= 3 ? '🔥' : undefined} />
        <Stat label="총 인증" value={`${me.totalCertifications}회`} />
        <Stat
          label="쌓인 벌금"
          value={formatPoints(me.totalPenaltyPoints)}
          tone={me.totalPenaltyPoints > 0 ? 'text-red-600' : undefined}
        />
      </dl>
      <Link
        to={`/crews/${crewId}/settlements`}
        className="flex items-center justify-between border-t border-stone-100 px-5 py-3 text-sm text-stone-600 hover:bg-stone-50"
      >
        랭킹 · 달력 · 정산 내역 보기
        <ChevronRight className="size-4 text-stone-400" aria-hidden />
      </Link>
    </section>
  )
}

function Stat({ label, value, emoji, tone }: { label: string; value: string; emoji?: string; tone?: string }) {
  return (
    <div className="px-2">
      <dt className="text-xs text-stone-500">{label}</dt>
      <dd className={`mt-1 text-lg font-bold tabular-nums ${tone ?? ''}`}>
        {emoji && <span aria-hidden>{emoji} </span>}
        {value}
      </dd>
    </div>
  )
}
