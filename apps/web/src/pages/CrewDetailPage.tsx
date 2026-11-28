import clsx from 'clsx'
import { CalendarDays, Check, Coins, Copy, Crown, LogOut, MoreVertical, Pencil, RefreshCw, Share2, Users } from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router'
import { FeedSection } from '../components/FeedSection'
import { MyStatsCard } from '../components/MyStatsCard'
import { TodayCard } from '../components/TodayCard'
import { Button, ConfirmDialog, ErrorState, PageHeader } from '../components/ui'
import { ApiError } from '../lib/api'
import { useCrew, useKickMember, useLeaveCrew, useRegenerateInviteCode, useTransferOwner } from '../lib/crews'
import { formatDays, formatInviteCode, formatPoints, inviteLink } from '../lib/format'
import type { CrewDetail, CrewMember } from '../lib/types'
import { useAuth } from '../providers/authContext'

type Pending =
  | { kind: 'leave' }
  | { kind: 'regenerate' }
  | { kind: 'kick'; member: CrewMember }
  | { kind: 'transfer'; member: CrewMember }

export function CrewDetailPage() {
  const id = Number(useParams().crewId)
  const navigate = useNavigate()
  const location = useLocation()
  const { data: crew, isPending, isError, error } = useCrew(id)
  const [pending, setPending] = useState<Pending | null>(null)
  const welcome = (location.state as { created?: boolean; joined?: boolean; certified?: boolean } | null) ?? {}

  if (isPending) {
    return (
      <div className="page-wide">
        <PageHeader />
        <div className="px-5">
          <div className="h-48 animate-pulse rounded-3xl bg-stone-200/60" />
        </div>
      </div>
    )
  }

  if (isError) {
    const notFound = error instanceof ApiError && error.status === 404
    return (
      <div className="page-wide">
        <PageHeader />
        <ErrorState
          message={notFound ? '크루를 찾을 수 없어요. 내보내졌거나 크루가 사라졌을 수 있어요.' : '크루 정보를 불러오지 못했어요.'}
          action={<Button onClick={() => navigate('/')}>홈으로</Button>}
        />
      </div>
    )
  }

  const isOwner = crew.myRole === 'OWNER'

  return (
    <div className="page-wide pb-12">
      <PageHeader
        right={
          isOwner && (
            <button
              onClick={() => navigate(`/crews/${id}/edit`)}
              aria-label="크루 정보 수정"
              className="flex size-10 items-center justify-center rounded-full text-stone-600 hover:bg-stone-100"
            >
              <Pencil className="size-5" />
            </button>
          )
        }
      />

      {/* 휴대폰: 한 줄로 (제목 → 오늘 현황·내 기록·초대·크루원 → 피드)
          넓은 화면(1280px~): 왼쪽에 제목과 피드, 오른쪽에 오늘 현황·내 기록·초대·크루원 */}
      <main className="flex flex-col gap-4 px-5 lg:mx-auto lg:max-w-2xl lg:px-0 xl:grid xl:max-w-none xl:grid-cols-[minmax(0,1fr)_22rem] xl:items-start xl:gap-x-10 xl:gap-y-6 2xl:grid-cols-[minmax(0,1fr)_24rem]">
        <div className="flex flex-col gap-4 xl:col-start-1">
        {(welcome.created || welcome.joined) && (
          <div className="rounded-2xl bg-brand-50 px-4 py-3 text-sm font-medium text-brand-700" role="status">
            {welcome.created ? '🎉 크루를 만들었어요! 아래 초대 코드를 친구에게 보내 보세요.' : '🎉 크루에 참여했어요!'}
          </div>
        )}
        {welcome.certified && (
          <div className="rounded-2xl bg-brand-50 px-4 py-3 text-sm font-medium text-brand-700" role="status">
            ✅ 인증을 올렸어요! 크루원들의 응원을 기다려 보세요.
          </div>
        )}

        <section>
          <h1 className="text-2xl font-bold leading-snug lg:text-3xl">{crew.name}</h1>
          {crew.description && <p className="mt-2 leading-relaxed text-stone-600">{crew.description}</p>}
          <div className="mt-4 flex flex-wrap gap-2">
            <InfoChip icon={<CalendarDays className="size-3.5" />}>{formatDays(crew.certDays)} 인증</InfoChip>
            <InfoChip icon={<Coins className="size-3.5" />}>빼먹으면 {formatPoints(crew.penaltyPoints)}</InfoChip>
            <InfoChip icon={<Users className="size-3.5" />}>
              {crew.members.length}/{crew.maxMembers}명
            </InfoChip>
          </div>
        </section>
        </div>

        <aside className="flex flex-col gap-4 xl:col-start-2 xl:row-span-2 xl:row-start-1" aria-label="오늘 현황과 크루 정보">
        <TodayCard crewId={crew.id} />

        <MyStatsCard crewId={crew.id} />

        <InviteCard crew={crew} onRegenerate={() => setPending({ kind: 'regenerate' })} />

        <section className="rounded-3xl border border-stone-200 bg-white">
          <h2 className="px-5 pt-5 pb-2 font-semibold">
            크루원 <span className="text-brand-600">{crew.members.length}</span>
          </h2>
          <ul className="pb-2">
            {crew.members.map((member) => (
              <MemberRow
                key={member.userId}
                member={member}
                canManage={isOwner && member.role !== 'OWNER'}
                onKick={() => setPending({ kind: 'kick', member })}
                onTransfer={() => setPending({ kind: 'transfer', member })}
              />
            ))}
          </ul>
        </section>
        <LeaveButton className="hidden xl:inline-flex" onClick={() => setPending({ kind: 'leave' })} />
        </aside>

        <div className="mt-2 xl:col-start-1 xl:mt-0">
          <FeedSection crewId={crew.id} />
        </div>

        <LeaveButton className="inline-flex xl:hidden" onClick={() => setPending({ kind: 'leave' })} />
      </main>

      <CrewActions crew={crew} pending={pending} onClose={() => setPending(null)} />
    </div>
  )
}

/** 휴대폰에서는 피드 아래, 데스크톱에서는 오른쪽 칸 맨 아래에 둔다 (className으로 어디서 보일지 정함) */
function LeaveButton({ className, onClick }: { className: string; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      className={clsx(
        'mx-auto mt-4 items-center gap-1.5 rounded-lg px-3 py-2 text-sm text-stone-400 hover:bg-stone-100 hover:text-red-600',
        className,
      )}
    >
      <LogOut className="size-4" aria-hidden />
      크루 나가기
    </button>
  )
}

function InfoChip({ icon, children }: { icon: ReactNode; children: ReactNode }) {
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full bg-white px-3 py-1.5 text-[13px] text-stone-700 ring-1 ring-stone-200">
      <span className="text-stone-400" aria-hidden>
        {icon}
      </span>
      {children}
    </span>
  )
}

function InviteCard({ crew, onRegenerate }: { crew: CrewDetail; onRegenerate: () => void }) {
  const [copied, setCopied] = useState<'code' | 'link' | null>(null)
  const full = crew.members.length >= crew.maxMembers

  useEffect(() => {
    if (!copied) return
    const timer = setTimeout(() => setCopied(null), 1800)
    return () => clearTimeout(timer)
  }, [copied])

  async function copy(kind: 'code' | 'link') {
    const text = kind === 'code' ? crew.inviteCode : inviteLink(crew.inviteCode)
    try {
      await navigator.clipboard.writeText(text)
      setCopied(kind)
    } catch {
      window.prompt('아래 내용을 복사해 주세요', text)
    }
  }

  async function share() {
    const url = inviteLink(crew.inviteCode)
    if (navigator.share) {
      try {
        await navigator.share({ title: `${crew.name} 크루 초대`, text: `HabitCrew에서 같이 인증해요! 초대 코드: ${crew.inviteCode}`, url })
        return
      } catch {
        // 사용자가 공유 창을 닫은 경우: 아무것도 하지 않는다
        return
      }
    }
    await copy('link')
  }

  return (
    <section className="rounded-3xl bg-stone-900 p-5 text-white">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-medium text-stone-300">초대 코드</h2>
        {crew.myRole === 'OWNER' && (
          <button
            onClick={onRegenerate}
            className="inline-flex items-center gap-1 rounded-lg px-2 py-1 text-xs text-stone-400 hover:bg-white/10 hover:text-white"
          >
            <RefreshCw className="size-3.5" aria-hidden />새 코드
          </button>
        )}
      </div>
      <button
        onClick={() => copy('code')}
        className="mt-2 flex w-full items-center justify-between rounded-2xl bg-white/10 px-4 py-3 text-left transition hover:bg-white/15"
        aria-label={`초대 코드 ${crew.inviteCode} 복사`}
      >
        <span className="font-mono text-2xl font-semibold tracking-[0.15em]">{formatInviteCode(crew.inviteCode)}</span>
        {copied === 'code' ? <Check className="size-5 text-brand-500" /> : <Copy className="size-5 text-stone-400" />}
      </button>
      <button
        onClick={share}
        disabled={full}
        className="mt-3 flex h-12 w-full items-center justify-center gap-2 rounded-xl bg-brand-600 text-[15px] font-semibold transition hover:bg-brand-700 disabled:cursor-not-allowed disabled:bg-white/10 disabled:text-stone-400"
      >
        {copied === 'link' ? <Check className="size-4" aria-hidden /> : <Share2 className="size-4" aria-hidden />}
        {full ? '정원이 가득 찼어요' : copied === 'link' ? '초대 링크를 복사했어요' : '초대 링크 보내기'}
      </button>
      <p className="sr-only" role="status">
        {copied === 'code' ? '초대 코드를 복사했어요' : copied === 'link' ? '초대 링크를 복사했어요' : ''}
      </p>
    </section>
  )
}

function MemberRow({
  member,
  canManage,
  onKick,
  onTransfer,
}: {
  member: CrewMember
  canManage: boolean
  onKick: () => void
  onTransfer: () => void
}) {
  const { user } = useAuth()
  const [menuOpen, setMenuOpen] = useState(false)
  const isMe = user?.id === member.userId

  return (
    <li className="relative flex items-center gap-3 px-5 py-2.5">
      <div
        className={clsx(
          'flex size-10 shrink-0 items-center justify-center rounded-full text-sm font-bold',
          member.role === 'OWNER' ? 'bg-amber-100 text-amber-700' : 'bg-brand-50 text-brand-700',
        )}
        aria-hidden
      >
        {member.nickname.slice(0, 1)}
      </div>
      <div className="min-w-0 flex-1">
        <p className="flex items-center gap-1.5 truncate font-medium">
          {member.nickname}
          {isMe && <span className="text-xs font-normal text-stone-400">(나)</span>}
          {member.role === 'OWNER' && <Crown className="size-3.5 text-amber-500" aria-label="크루장" />}
        </p>
        <p className="text-xs text-stone-400">
          {new Date(member.joinedAt).toLocaleDateString('ko-KR', { month: 'long', day: 'numeric' })} 참여
        </p>
      </div>
      {canManage && (
        <>
          <button
            onClick={() => setMenuOpen((v) => !v)}
            aria-label={`${member.nickname} 관리`}
            aria-expanded={menuOpen}
            className="flex size-9 items-center justify-center rounded-full text-stone-400 hover:bg-stone-100"
          >
            <MoreVertical className="size-4" />
          </button>
          {menuOpen && (
            <>
              <div className="fixed inset-0 z-10" onClick={() => setMenuOpen(false)} aria-hidden />
              <div className="absolute top-12 right-5 z-20 w-40 overflow-hidden rounded-xl bg-white py-1 shadow-lg ring-1 ring-stone-200">
                <button
                  onClick={() => {
                    setMenuOpen(false)
                    onTransfer()
                  }}
                  className="block w-full px-4 py-2.5 text-left text-sm hover:bg-stone-50"
                >
                  크루장 넘기기
                </button>
                <button
                  onClick={() => {
                    setMenuOpen(false)
                    onKick()
                  }}
                  className="block w-full px-4 py-2.5 text-left text-sm text-red-600 hover:bg-red-50"
                >
                  내보내기
                </button>
              </div>
            </>
          )}
        </>
      )}
    </li>
  )
}

/** 나가기·새 코드·내보내기·크루장 넘기기 확인 창 */
function CrewActions({ crew, pending, onClose }: { crew: CrewDetail; pending: Pending | null; onClose: () => void }) {
  const navigate = useNavigate()
  const leave = useLeaveCrew(crew.id)
  const regenerate = useRegenerateInviteCode(crew.id)
  const kick = useKickMember(crew.id)
  const transfer = useTransferOwner(crew.id)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => setError(null), [pending])

  const isOwner = crew.myRole === 'OWNER'
  const alone = crew.members.length === 1

  const config = (() => {
    switch (pending?.kind) {
      case 'leave':
        if (isOwner && !alone) {
          return {
            title: '크루장은 바로 나갈 수 없어요',
            description: '크루원 목록에서 다른 크루원에게 크루장을 넘긴 뒤에 나갈 수 있어요.',
            confirmLabel: '확인',
            danger: false,
            run: async () => onClose(),
          }
        }
        return {
          title: '크루를 나갈까요?',
          description: alone
            ? '혼자 남은 크루라서, 나가면 크루가 사라져요.'
            : '다시 참여하려면 초대 코드가 필요해요.',
          confirmLabel: '나가기',
          danger: true,
          run: async () => {
            await leave.mutateAsync()
            navigate('/', { replace: true })
          },
        }
      case 'regenerate':
        return {
          title: '초대 코드를 새로 만들까요?',
          description: '지금 코드와 초대 링크로는 더 이상 참여할 수 없어요. 이미 참여한 크루원은 그대로예요.',
          confirmLabel: '새로 만들기',
          danger: false,
          run: async () => {
            await regenerate.mutateAsync()
            onClose()
          },
        }
      case 'kick':
        return {
          title: `${pending.member.nickname}님을 내보낼까요?`,
          description: '내보낸 크루원은 초대 코드로 다시 참여할 수 있어요.',
          confirmLabel: '내보내기',
          danger: true,
          run: async () => {
            await kick.mutateAsync(pending.member.userId)
            onClose()
          },
        }
      case 'transfer':
        return {
          title: `${pending.member.nickname}님에게 크루장을 넘길까요?`,
          description: '넘기면 크루 정보 수정, 초대 코드 관리, 크루원 내보내기를 할 수 없게 돼요.',
          confirmLabel: '넘기기',
          danger: false,
          run: async () => {
            await transfer.mutateAsync(pending.member.userId)
            onClose()
          },
        }
      default:
        return null
    }
  })()

  const loading = leave.isPending || regenerate.isPending || kick.isPending || transfer.isPending

  return (
    <ConfirmDialog
      open={!!config}
      title={config?.title ?? ''}
      description={config?.description}
      confirmLabel={config?.confirmLabel ?? ''}
      danger={config?.danger}
      loading={loading}
      error={error}
      onClose={onClose}
      onConfirm={async () => {
        if (!config) return
        setError(null)
        try {
          await config.run()
        } catch (err) {
          setError(err instanceof ApiError ? err.message : '처리하지 못했어요.')
        }
      }}
    />
  )
}
