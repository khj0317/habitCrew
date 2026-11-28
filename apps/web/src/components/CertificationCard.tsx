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
    <article className="overflow-hidden rounded-3xl border border-stone-200 bg-white">
      <header className="flex items-center gap-2.5 px-4 py-3">
        <Avatar userId={c.author.userId} nickname={c.author.nickname} size="sm" />
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold">
            {c.author.nickname}
            {c.mine && <span className="ml-1 text-xs font-normal text-stone-400">(나)</span>}
          </p>
        </div>
        <time dateTime={c.createdAt} className="text-xs text-stone-400">
          {formatRelative(c.createdAt)}
        </time>
      </header>

      <Link to={detail} className="block bg-stone-100" aria-label={`${c.author.nickname}님의 인증 사진 크게 보기`}>
        {/* 휴대폰은 480px 썸네일(데이터 절약), 넓은 화면은 카드가 커서 1600px 사진을 쓴다 (작은 사진을 늘리면 흐려진다) */}
        <picture>
          <source media="(min-width: 1280px)" srcSet={c.imageUrl} />
          <img
            src={c.thumbnailUrl}
            alt=""
            loading="lazy"
            decoding="async"
            // 사진 비율대로 자리를 미리 잡아 두어, 사진이 늦게 떠도 화면이 덜컹거리지 않게 한다
            style={{ aspectRatio: `${c.imageWidth} / ${c.imageHeight}` }}
            className="max-h-[28rem] w-full object-cover"
          />
        </picture>
      </Link>

      <div className="px-4 pt-3 pb-4">
        {c.message && <p className="mb-2 leading-relaxed whitespace-pre-wrap text-stone-800">{c.message}</p>}
        <div className="-ml-2 flex items-center gap-1">
          <button
            onClick={() => toggleLike.mutate({ id: c.id, like: !c.likedByMe })}
            aria-pressed={c.likedByMe}
            aria-label={c.likedByMe ? '좋아요 취소' : '좋아요'}
            className="inline-flex h-9 items-center gap-1.5 rounded-full px-2 text-sm text-stone-600 hover:bg-stone-100"
          >
            <Heart
              className={clsx('size-5 transition', c.likedByMe ? 'fill-rose-500 text-rose-500' : 'text-stone-500')}
            />
            <span className="tabular-nums">{c.likeCount}</span>
          </button>
          <Link
            to={detail}
            className="inline-flex h-9 items-center gap-1.5 rounded-full px-2 text-sm text-stone-600 hover:bg-stone-100"
            aria-label={`댓글 ${c.commentCount}개`}
          >
            <MessageCircle className="size-5 text-stone-500" />
            <span className="tabular-nums">{c.commentCount}</span>
          </Link>
        </div>
      </div>
    </article>
  )
}
