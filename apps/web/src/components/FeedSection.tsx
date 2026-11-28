import { useEffect, useRef } from 'react'
import { useFeed } from '../lib/certifications'
import { CertificationCard } from './CertificationCard'
import { Button } from './ui'

/** 크루 인증 피드: 맨 아래에 닿으면 다음 10개를 자동으로 불러온다 */
export function FeedSection({ crewId }: { crewId: number }) {
  const { data, isPending, isError, refetch, fetchNextPage, hasNextPage, isFetchingNextPage } = useFeed(crewId)
  const sentinel = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const el = sentinel.current
    if (!el || !hasNextPage) return
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && !isFetchingNextPage) fetchNextPage()
      },
      { rootMargin: '400px' },
    )
    observer.observe(el)
    return () => observer.disconnect()
  }, [hasNextPage, isFetchingNextPage, fetchNextPage])

  const items = data?.pages.flatMap((page) => page.items) ?? []

  return (
    <section aria-labelledby="feed-title">
      <h2 id="feed-title" className="mb-3 px-1 text-lg font-black">
        인증 피드
      </h2>
      {isPending ? (
        <div className="flex flex-col gap-4">
          {[0, 1].map((i) => (
            <div key={i} className="h-80 animate-pulse rounded-2xl border-2 border-stone-200 bg-stone-100" />
          ))}
        </div>
      ) : isError ? (
        <div className="card p-8 text-center text-sm text-stone-500">
          피드를 불러오지 못했어요.
          <Button variant="secondary" className="mt-4 w-full" onClick={() => refetch()}>
            다시 시도
          </Button>
        </div>
      ) : items.length === 0 ? (
        <div className="card px-6 py-12 text-center">
          <div className="text-3xl">📸</div>
          <p className="mt-3 font-medium">아직 올라온 인증이 없어요</p>
          <p className="mt-1 text-sm text-stone-500">첫 번째 인증의 주인공이 되어 보세요!</p>
        </div>
      ) : (
        <ul className="flex flex-col gap-4">
          {items.map((c) => (
            <li key={c.id}>
              <CertificationCard crewId={crewId} certification={c} />
            </li>
          ))}
        </ul>
      )}
      <div ref={sentinel} />
      {isFetchingNextPage && <div className="mt-4 h-24 animate-pulse rounded-2xl border-2 border-stone-200 bg-stone-100" />}
      {hasNextPage && !isFetchingNextPage && (
        <Button variant="ghost" className="mt-2 w-full" onClick={() => fetchNextPage()}>
          더 보기
        </Button>
      )}
    </section>
  )
}
