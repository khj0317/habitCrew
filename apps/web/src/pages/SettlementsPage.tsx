import { useState } from 'react'
import { useParams } from 'react-router'
import { CalendarCard, MonthSwitcher, RankingCard } from '../components/RecordSections'
import { Avatar, Button, ErrorState, PageHeader } from '../components/ui'
import { useCrew } from '../lib/crews'
import { formatPoints } from '../lib/format'
import { currentMonth } from '../lib/records'
import { useCrewStats, useSettlementHistory } from '../lib/settlements'

/** 크루 기록: 월간 랭킹, 인증 달력, 크루원 누적 기록, 날짜별 정산 내역 */
export function SettlementsPage() {
  const crewId = Number(useParams().crewId)
  const { data: crew } = useCrew(crewId)
  const { data: stats } = useCrewStats(crewId)
  const { data, isPending, isError, hasNextPage, fetchNextPage, isFetchingNextPage } = useSettlementHistory(crewId)
  const items = data?.pages.flatMap((p) => p.items) ?? []
  const [month, setMonth] = useState(currentMonth)
  const [calendarUserId, setCalendarUserId] = useState<number | null>(null)

  return (
    <div className="page-wide pb-12">
      <PageHeader title={crew ? `${crew.name} 기록` : '기록'} back={`/crews/${crewId}`} />
      {/* 데스크톱: 랭킹|달력, 크루원 기록|정산 내역 두 칸 */}
      <main className="flex flex-col gap-6 px-5 lg:grid lg:grid-cols-2 lg:items-start lg:px-0">
        {stats && (
          <>
            <div className="lg:col-span-2">
              <MonthSwitcher month={month} onChange={setMonth} />
            </div>
            <RankingCard crewId={crewId} month={month} myUserId={stats.me.userId} />
            <CalendarCard
              crewId={crewId}
              month={month}
              members={stats.members}
              selectedUserId={calendarUserId ?? stats.me.userId}
              onSelectUser={setCalendarUserId}
            />
          </>
        )}
        {stats && (
          <section className="rounded-3xl border border-stone-200 bg-white p-5">
            <h2 className="font-semibold">크루원 기록</h2>
            <p className="mt-0.5 text-xs text-stone-500">연속 인증이 긴 순서예요</p>
            <ol className="mt-4 flex flex-col gap-3">
              {stats.members.map((m, index) => (
                <li key={m.userId} className="flex items-center gap-3">
                  <span className="w-5 text-center text-sm font-bold text-stone-400 tabular-nums">{index + 1}</span>
                  <Avatar userId={m.userId} nickname={m.nickname} size="sm" />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium">
                      {m.nickname}
                      {m.userId === stats.me.userId && <span className="ml-1 text-xs text-stone-400">(나)</span>}
                    </p>
                    <p className="text-xs text-stone-500">
                      인증 {m.totalCertifications}회 · 빼먹음 {m.missedCount}회
                    </p>
                  </div>
                  <div className="text-right">
                    <p className="text-sm font-semibold tabular-nums">
                      {m.currentStreak >= 3 && '🔥 '}
                      {m.currentStreak}일 연속
                    </p>
                    <p
                      className={`text-xs tabular-nums ${m.totalPenaltyPoints > 0 ? 'text-red-600' : 'text-stone-400'}`}
                    >
                      벌금 {formatPoints(m.totalPenaltyPoints)}
                    </p>
                  </div>
                </li>
              ))}
            </ol>
          </section>
        )}

        <section>
          <h2 className="mb-1 px-1 font-semibold">날짜별 정산</h2>
          <p className="mb-3 px-1 text-xs text-stone-500">매일 0시 5분에 전날 인증을 확인해 벌금을 매겨요</p>
          {isPending ? (
            <div className="h-32 animate-pulse rounded-3xl bg-stone-200/60" />
          ) : isError ? (
            <ErrorState message="정산 내역을 불러오지 못했어요." />
          ) : items.length === 0 ? (
            <div className="rounded-3xl bg-white px-6 py-12 text-center ring-1 ring-stone-200">
              <div className="text-3xl">🧾</div>
              <p className="mt-3 font-medium">아직 정산된 날이 없어요</p>
              <p className="mt-1 text-sm text-stone-500">첫 인증일이 지나면 여기에 기록돼요.</p>
            </div>
          ) : (
            <ul className="flex flex-col gap-3">
              {items.map((s) => {
                const allDone = s.penalties.length === 0
                return (
                  <li key={s.date} className="rounded-2xl border border-stone-200 bg-white px-4 py-4">
                    <div className="flex items-baseline justify-between">
                      <p className="font-semibold">
                        {new Date(s.date + 'T00:00:00').toLocaleDateString('ko-KR', {
                          month: 'long',
                          day: 'numeric',
                          weekday: 'short',
                        })}
                      </p>
                      <p className="text-sm text-stone-500 tabular-nums">
                        {s.targetCount}명 중 {s.certifiedCount}명 인증
                      </p>
                    </div>
                    {s.targetCount === 0 ? (
                      <p className="mt-2 text-sm text-stone-400">대상 크루원이 없었어요 (첫날 참여자는 정산하지 않아요)</p>
                    ) : allDone ? (
                      <p className="mt-2 text-sm font-medium text-brand-700">🎉 모두 인증했어요!</p>
                    ) : (
                      <ul className="mt-3 flex flex-wrap gap-1.5" aria-label="벌금을 받은 크루원">
                        {s.penalties.map((p) => (
                          <li
                            key={p.userId}
                            className="inline-flex items-center gap-1 rounded-full bg-red-50 px-2.5 py-1 text-xs text-red-700"
                          >
                            {p.nickname}
                            <span className="font-semibold tabular-nums">−{formatPoints(p.points)}</span>
                          </li>
                        ))}
                      </ul>
                    )}
                  </li>
                )
              })}
            </ul>
          )}
          {hasNextPage && (
            <Button variant="ghost" className="mt-3 w-full" loading={isFetchingNextPage} onClick={() => fetchNextPage()}>
              이전 기록 더 보기
            </Button>
          )}
        </section>
      </main>
    </div>
  )
}
