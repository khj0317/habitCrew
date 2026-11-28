import clsx from 'clsx'
import { Heart, MessageCircle } from 'lucide-react'
import { Link } from 'react-router'
import { useToggleLike } from '../lib/certifications'
import { formatRelative } from '../lib/format'
import type { Certification } from '../lib/types'
import { Avatar } from './ui'

export function CertificationCard({ crewId, certification: c }: { crewId: number; certification: Certification }) {
  const toggleLike = useToggleLike(crewId)
  const detail = `/crews/${crewId}/certifications/${c.id}`

  return (
    <article className="card overflow-hidden">
      <header className="flex items-center gap-2.5 px-4 py-3">
        <Avatar userId={c.author.userId} nickname={c.author.nickname} size="sm" />
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-extrabold">
            {c.author.nickname}
            {c.mine && <span className="ml-1 text-xs font-normal text-stone-400">(나)</span>}
          </p>
        </div>
        <time dateTime={c.createdAt} className="text-xs text-stone-400">
          {formatRelative(c.createdAt)}
        </time>
      </header>

      <Link
        to={detail}
        className="relative block border-y-2 border-ink bg-stone-100"
        aria-label={`${c.author.nickname}님의 인증 사진 크게 보기`}
      >
        {c.thumbnailUrl ? (
          <>
            {/* 휴대폰은 480px 썸네일(데이터 절약), 넓은 화면은 카드가 커서 1600px 사진을 쓴다 (작은 사진을 늘리면 흐려진다) */}
            <picture>
              {c.imageUrl && <source media="(min-width: 1280px)" srcSet={c.imageUrl} />}
              <img
                src={c.thumbnailUrl}
                alt=""
                loading="lazy"
                decoding="async"
                // 사진 비율대로 자리를 미리 잡아 두어, 사진이 늦게 떠도 화면이 덜컹거리지 않게 한다
                style={{ aspectRatio: `${c.imageWidth} / ${c.imageHeight}` }}
                className={clsx('max-h-[28rem] w-full object-cover', c.hidden && 'opacity-60 grayscale')}
              />
            </picture>
            {c.hidden && (
              <span className="absolute top-3 left-3 rounded-md border-2 border-ink bg-amber-200 px-2 py-0.5 text-xs font-bold text-ink">
                🙈 가려진 사진 · 크루원에게 안 보여요
              </span>
            )}
          </>
        ) : (
          <div className="flex aspect-[4/3] flex-col items-center justify-center gap-2 text-stone-500">
            <span className="text-3xl" aria-hidden>
              🙈
            </span>
            <span className="text-sm">신고로 가려진 사진이에요</span>
          </div>
        )}
      </Link>

      <div className="px-4 pt-3 pb-4">
        {c.message && <p className="mb-2 leading-relaxed whitespace-pre-wrap text-stone-800">{c.message}</p>}
        <div className="-ml-2 flex items-center gap-1">
          <button
            onClick={() => toggleLike.mutate({ id: c.id, like: !c.likedByMe })}
            aria-pressed={c.likedByMe}
            aria-label={c.likedByMe ? '좋아요 취소' : '좋아요'}
            className="inline-flex h-9 items-center gap-1.5 rounded-lg px-2 text-sm font-bold text-ink hover:bg-stone-100"
          >
            <Heart
              className={clsx('size-5 transition', c.likedByMe ? 'fill-coral-400 text-ink' : 'text-ink')}
            />
            <span className="tabular-nums">{c.likeCount}</span>
          </button>
          <Link
            to={detail}
            className="inline-flex h-9 items-center gap-1.5 rounded-lg px-2 text-sm font-bold text-ink hover:bg-stone-100"
            aria-label={`댓글 ${c.commentCount}개`}
          >
            <MessageCircle className="size-5 text-ink" />
            <span className="tabular-nums">{c.commentCount}</span>
          </Link>
        </div>
      </div>
    </article>
  )
}
