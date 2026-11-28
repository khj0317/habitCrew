import { ChevronRight } from 'lucide-react'
import { Link } from 'react-router'
import { formatPoints } from '../lib/format'
import { useCrewStats } from '../lib/settlements'

/** 내 기록 요약: 연속 인증 · 총 인증 · 쌓인 벌금, 그리고 정산 내역으로 가는 길 */
export function MyStatsCard({ crewId }: { crewId: number }) {
  const { data, isPending, isError } = useCrewStats(crewId)
  if (isPending) return <div className="h-24 animate-pulse rounded-2xl border-2 border-stone-200 bg-stone-100" />
  if (isError) return null
  const me = data.me

  return (
    <section className="card">
      <h2 className="sr-only">내 기록</h2>
      <dl className="grid grid-cols-3 divide-x-2 divide-dashed divide-stone-200 py-4 text-center">
        <Stat label="연속 인증" value={`${me.currentStreak}일`} emoji={me.currentStreak >= 3 ? '🔥' : undefined} />
        <Stat label="총 인증" value={`${me.totalCertifications}회`} />
        <Stat
          label="쌓인 벌금"
          value={formatPoints(me.totalPenaltyPoints)}
          tone={me.totalPenaltyPoints > 0 ? 'text-coral-600' : undefined}
        />
      </dl>
      <Link
        to={`/crews/${crewId}/settlements`}
        className="flex items-center justify-between rounded-b-[14px] border-t-2 border-ink bg-stone-50 px-5 py-3 text-sm font-bold text-ink hover:bg-lime-300"
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
      <dd className={`mt-1 text-xl font-black tabular-nums ${tone ?? ''}`}>
        {emoji && <span aria-hidden>{emoji} </span>}
        {value}
      </dd>
    </div>
  )
}
