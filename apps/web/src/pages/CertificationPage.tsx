import clsx from 'clsx'
import { Heart, Send, Trash2 } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router'
import { Alert, Avatar, Button, ConfirmDialog, ErrorState, PageHeader } from '../components/ui'
import { ApiError } from '../lib/api'
import {
  useAddComment,
  useCertification,
  useComments,
  useDeleteCertification,
  useDeleteComment,
  useToday,
  useToggleLike,
} from '../lib/certifications'
import { formatRelative } from '../lib/format'

/** 인증 하나를 크게 보고 댓글을 단다 */
export function CertificationPage() {
  const params = useParams()
  const crewId = Number(params.crewId)
  const id = Number(params.certificationId)
  const navigate = useNavigate()
  const { data: c, isPending, isError } = useCertification(id)
  const { data: today } = useToday(crewId)
  const toggleLike = useToggleLike(crewId)
  const remove = useDeleteCertification(crewId)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  if (isPending) {
    return (
      <div className="page-wide">
        <PageHeader back={`/crews/${crewId}`} />
        <div className="mx-5 aspect-square animate-pulse rounded-3xl bg-stone-200/60" />
      </div>
    )
  }
  if (isError) {
    return (
      <div className="page-wide">
        <PageHeader back={`/crews/${crewId}`} />
        <ErrorState message="인증을 찾을 수 없어요. 지워졌을 수 있어요." />
      </div>
    )
  }

  // 오늘 올린 내 인증만 지울 수 있다 (지난 인증은 정산 근거)
  const canDelete = c.mine && today?.date === c.certDate

  return (
    <div className="page-wide pb-28 lg:pb-0">
      <PageHeader
        back={`/crews/${crewId}`}
        title={`${c.author.nickname}님의 인증`}
        right={
          canDelete && (
            <button
              onClick={() => setConfirmDelete(true)}
              aria-label="인증 지우기"
              className="flex size-10 items-center justify-center rounded-full text-stone-500 hover:bg-stone-100 hover:text-red-600"
            >
              <Trash2 className="size-5" />
            </button>
          )
        }
      />

      {/* 넓은 화면(1280px~): 왼쪽 사진, 오른쪽 내용·댓글 */}
      <div className="xl:grid xl:grid-cols-[minmax(0,1fr)_24rem] xl:items-start xl:gap-8">
      <div className="bg-stone-900 lg:overflow-hidden lg:rounded-3xl">
        <img
          src={c.imageUrl}
          alt={`${c.author.nickname}님의 인증 사진`}
          style={{ aspectRatio: `${c.imageWidth} / ${c.imageHeight}` }}
          className="mx-auto max-h-[70vh] w-full object-contain"
        />
      </div>

      <div className="lg:rounded-3xl lg:border lg:border-stone-200 lg:bg-white lg:pb-5">
      <section className="px-5 pt-4">
        <div className="flex items-center gap-2.5">
          <Avatar userId={c.author.userId} nickname={c.author.nickname} size="sm" />
          <p className="flex-1 text-sm font-semibold">{c.author.nickname}</p>
          <time dateTime={c.createdAt} className="text-xs text-stone-400">
            {new Date(c.createdAt).toLocaleString('ko-KR', {
              month: 'long',
              day: 'numeric',
              hour: 'numeric',
              minute: '2-digit',
            })}
          </time>
        </div>
        {c.message && <p className="mt-3 leading-relaxed whitespace-pre-wrap">{c.message}</p>}
        <button
          onClick={() => toggleLike.mutate({ id: c.id, like: !c.likedByMe })}
          aria-pressed={c.likedByMe}
          className={clsx(
            'mt-4 inline-flex h-10 items-center gap-2 rounded-full px-4 text-sm font-medium ring-1 transition',
            c.likedByMe ? 'bg-rose-50 text-rose-600 ring-rose-200' : 'bg-white text-stone-600 ring-stone-200',
          )}
        >
          <Heart className={clsx('size-4', c.likedByMe && 'fill-rose-500 text-rose-500')} aria-hidden />
          좋아요 {c.likeCount}
        </button>
      </section>

      <Comments crewId={crewId} certificationId={id} count={c.commentCount} />
      </div>
      </div>

      <ConfirmDialog
        open={confirmDelete}
        title="인증을 지울까요?"
        description="지우면 오늘 인증하지 않은 것으로 돼요. 오늘 안에 다시 올릴 수 있어요."
        confirmLabel="지우기"
        danger
        loading={remove.isPending}
        error={deleteError}
        onClose={() => {
          setConfirmDelete(false)
          setDeleteError(null)
        }}
        onConfirm={async () => {
          try {
            await remove.mutateAsync(id)
            navigate(`/crews/${crewId}`, { replace: true })
          } catch (err) {
            setDeleteError(err instanceof ApiError ? err.message : '지우지 못했어요.')
          }
        }}
      />
    </div>
  )
}

function Comments({ crewId, certificationId, count }: { crewId: number; certificationId: number; count: number }) {
  const { data: comments, isPending } = useComments(certificationId)
  const add = useAddComment(crewId, certificationId)
  const remove = useDeleteComment(crewId, certificationId)
  const [content, setContent] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (!content.trim()) return
    setError(null)
    try {
      await add.mutateAsync(content)
      setContent('')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '댓글을 달지 못했어요.')
    }
  }

  return (
    <section className="mt-6 border-t border-stone-200 px-5 pt-5">
      <h2 className="font-semibold">
        댓글 <span className="text-brand-600">{count}</span>
      </h2>
      {isPending ? (
        <div className="mt-4 h-12 animate-pulse rounded-xl bg-stone-200/60" />
      ) : comments!.length === 0 ? (
        <p className="py-8 text-center text-sm text-stone-400">첫 응원 댓글을 남겨 보세요 💬</p>
      ) : (
        <ul className="mt-3 flex flex-col gap-4">
          {comments!.map((comment) => (
            <li key={comment.id} className="group flex gap-2.5">
              <Avatar userId={comment.author.userId} nickname={comment.author.nickname} size="sm" />
              <div className="min-w-0 flex-1">
                <p className="text-sm">
                  <span className="font-semibold">{comment.author.nickname}</span>
                  <span className="ml-2 text-xs text-stone-400">{formatRelative(comment.createdAt)}</span>
                </p>
                <p className="mt-0.5 text-[15px] leading-relaxed break-words whitespace-pre-wrap text-stone-800">
                  {comment.content}
                </p>
              </div>
              {comment.mine && (
                <button
                  onClick={() => remove.mutate(comment.id)}
                  aria-label="댓글 지우기"
                  className="self-start rounded-full p-1.5 text-stone-300 hover:bg-stone-100 hover:text-red-600"
                >
                  <Trash2 className="size-4" />
                </button>
              )}
            </li>
          ))}
        </ul>
      )}

      <form
        onSubmit={onSubmit}
        className="fixed inset-x-0 bottom-0 z-10 mx-auto flex w-full max-w-md items-end gap-2 border-t border-stone-200 bg-white px-4 pt-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] lg:static lg:mt-5 lg:max-w-none lg:border-0 lg:px-0 lg:pb-0"
      >
        <div className="flex-1">
          {error && (
            <div className="mb-2">
              <Alert>{error}</Alert>
            </div>
          )}
          <label htmlFor="comment-input" className="sr-only">
            댓글
          </label>
          <input
            id="comment-input"
            value={content}
            onChange={(e) => setContent(e.target.value)}
            placeholder="응원의 한마디를 남겨 주세요"
            maxLength={300}
            className="h-11 w-full rounded-full border border-stone-200 bg-stone-50 px-4 text-[15px] outline-none focus:border-brand-600 focus:bg-white focus:ring-4 focus:ring-brand-100"
          />
        </div>
        <Button
          type="submit"
          aria-label="댓글 보내기"
          className="!h-11 !w-11 shrink-0 !rounded-full !px-0"
          disabled={!content.trim()}
          loading={add.isPending}
        >
          {!add.isPending && <Send className="size-4" />}
        </Button>
      </form>
    </section>
  )
}
