import clsx from 'clsx'
import { Camera, CheckCircle2, Coffee } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { useToday } from '../lib/certifications'
import { formatRemaining } from '../lib/format'
import { Avatar, Button } from './ui'

/** 크루 상세 맨 위: 오늘 인증했는지, 마감까지 얼마 남았는지, 누가 인증했는지 */
export function TodayCard({ crewId }: { crewId: number }) {
  const navigate = useNavigate()
  const { data: today, isPending, isError } = useToday(crewId)
  const now = useNow(30_000)

  if (isPending) return <div className="h-40 animate-pulse rounded-3xl bg-stone-200/60" />
  if (isError) return null

  const done = today.myCertificationId !== null
  const progress = today.memberCount === 0 ? 0 : today.certifiedCount / today.memberCount

  return (
    <section
      className={clsx(
        'rounded-3xl p-5',
        !today.certDay ? 'bg-stone-100' : done ? 'bg-brand-50' : 'bg-white ring-2 ring-brand-500',
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-xs font-medium text-stone-500">
            {new Date(today.date + 'T00:00:00').toLocaleDateString('ko-KR', {
              month: 'long',
              day: 'numeric',
              weekday: 'short',
            })}
          </p>
          <h2 className="mt-1 text-lg font-bold">
            {!today.certDay ? '오늘은 쉬는 날이에요' : done ? '오늘 인증 완료! 🎉' : '오늘 인증이 남았어요'}
          </h2>
          {today.certDay && !done && (
            <p className="mt-0.5 text-sm font-medium text-brand-700">{formatRemaining(today.deadline, now)}</p>
          )}
        </div>
        {!today.certDay ? (
          <Coffee className="size-7 text-stone-400" aria-hidden />
        ) : done ? (
          <CheckCircle2 className="size-7 text-brand-600" aria-hidden />
        ) : null}
      </div>

      {today.certDay && (
        <>
          <div className="mt-4 flex items-center gap-3">
            <div
              className="h-2 flex-1 overflow-hidden rounded-full bg-stone-200"
              role="progressbar"
              aria-valuemin={0}
              aria-valuemax={today.memberCount}
              aria-valuenow={today.certifiedCount}
              aria-label="오늘 인증한 크루원"
            >
              <div className="h-full rounded-full bg-brand-500 transition-all" style={{ width: `${progress * 100}%` }} />
            </div>
            <span className="text-sm font-semibold tabular-nums text-stone-600">
              {today.certifiedCount}/{today.memberCount}
            </span>
          </div>
          <ul className="mt-3 flex flex-wrap gap-1.5" aria-label="크루원 인증 현황">
            {today.members.map((m) => (
              <li key={m.userId} className="relative" title={`${m.nickname} · ${m.certified ? '인증 완료' : '아직'}`}>
                <Avatar userId={m.userId} nickname={m.nickname} size="sm" dimmed={!m.certified} />
                {m.certified && (
                  <CheckCircle2
                    className="absolute -right-0.5 -bottom-0.5 size-3.5 rounded-full bg-white fill-brand-600 text-white"
                    aria-hidden
                  />
                )}
                <span className="sr-only">
                  {m.nickname} {m.certified ? '인증 완료' : '아직 안 함'}
                </span>
              </li>
            ))}
          </ul>
          {!done && (
            <Button className="mt-5 w-full" onClick={() => navigate(`/crews/${crewId}/certify`)}>
              <Camera className="size-4" aria-hidden />
              사진으로 인증하기
            </Button>
          )}
        </>
      )}
    </section>
  )
}

/** 남은 시간 표시를 주기적으로 갱신한다 */
function useNow(intervalMs: number) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), intervalMs)
    return () => clearInterval(timer)
  }, [intervalMs])
  return now
}
