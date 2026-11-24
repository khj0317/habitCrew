import { CalendarDays, Coins, Crown, Users } from 'lucide-react'
import { useState, type FormEvent, type ReactNode } from 'react'
import { useNavigate, useParams } from 'react-router'
import { Alert, Button, PageHeader } from '../components/ui'
import { ApiError } from '../lib/api'
import { useInvitePreview, useJoinCrew } from '../lib/crews'
import { formatDays, formatInviteCode, formatPoints } from '../lib/format'

/** 소문자·공백·하이픈이 섞여도 서버와 같은 규칙으로 정리한다 */
const normalize = (code: string) => code.replace(/[\s-]/g, '').toUpperCase()

/** /join: 초대 코드 입력, /join/:code: 초대 링크로 들어왔을 때 크루 미리보기 */
export function JoinCrewPage() {
  const { code } = useParams()
  return code ? <InvitePreviewView code={normalize(code)} /> : <EnterCodeView />
}

function EnterCodeView() {
  const navigate = useNavigate()
  const [code, setCode] = useState('')
  const valid = normalize(code).length === 8

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (valid) navigate(`/join/${normalize(code)}`)
  }

  return (
    <div className="mx-auto min-h-dvh w-full max-w-md">
      <PageHeader title="초대 코드로 참여" />
      <form onSubmit={onSubmit} className="flex flex-col px-5 pt-6">
        <label htmlFor="invite-code" className="text-xl font-bold leading-snug">
          크루장에게 받은
          <br />
          초대 코드를 입력해 주세요
        </label>
        <input
          id="invite-code"
          value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase())}
          placeholder="ABCD-2345"
          autoComplete="off"
          autoCapitalize="characters"
          spellCheck={false}
          maxLength={12}
          className="mt-8 h-16 rounded-2xl border border-stone-200 bg-white text-center font-mono text-2xl font-semibold tracking-[0.2em] outline-none transition placeholder:text-stone-300 focus:border-brand-600 focus:ring-4 focus:ring-brand-100"
        />
        <p className="mt-3 text-center text-[13px] text-stone-500">영문과 숫자 8자리예요</p>
        <Button type="submit" disabled={!valid} className="mt-8">
          다음
        </Button>
      </form>
    </div>
  )
}

function InvitePreviewView({ code }: { code: string }) {
  const navigate = useNavigate()
  const { data: crew, isPending, error } = useInvitePreview(code)
  const joinCrew = useJoinCrew()
  const [joinError, setJoinError] = useState<string | null>(null)

  async function onJoin() {
    setJoinError(null)
    try {
      const joined = await joinCrew.mutateAsync(code)
      navigate(`/crews/${joined.id}`, { replace: true, state: { joined: true } })
    } catch (err) {
      setJoinError(err instanceof ApiError ? err.message : '참여하지 못했어요.')
    }
  }

  return (
    <div className="mx-auto flex min-h-dvh w-full max-w-md flex-col">
      <PageHeader title="크루 초대" back="/join" />
      <main className="flex flex-1 flex-col px-5 pt-4 pb-8">
        {isPending ? (
          <div className="h-72 animate-pulse rounded-3xl bg-stone-200/60" aria-label="불러오는 중" />
        ) : error || !crew ? (
          <div className="flex flex-col items-center py-16 text-center">
            <div className="text-4xl">🔍</div>
            <p className="mt-4 font-semibold">초대 코드를 찾을 수 없어요</p>
            <p className="mt-1 text-sm text-stone-500">
              <span className="font-mono">{formatInviteCode(code)}</span>
              <br />
              크루장이 코드를 바꿨을 수도 있어요. 다시 확인해 주세요.
            </p>
            <Button variant="secondary" className="mt-8" onClick={() => navigate('/join')}>
              코드 다시 입력
            </Button>
          </div>
        ) : (
          <>
            <section className="rounded-3xl border border-stone-200 bg-white p-6">
              <p className="inline-flex items-center gap-1 text-[13px] text-stone-500">
                <Crown className="size-3.5 text-amber-500" aria-hidden />
                {crew.ownerNickname}님의 크루
              </p>
              <h2 className="mt-2 text-2xl font-bold leading-snug">{crew.name}</h2>
              {crew.description && <p className="mt-2 leading-relaxed text-stone-600">{crew.description}</p>}
              <dl className="mt-6 grid grid-cols-3 gap-2 text-center">
                <Stat icon={<CalendarDays className="size-4" />} label="인증 요일" value={formatDays(crew.certDays)} />
                <Stat
                  icon={<Users className="size-4" />}
                  label="인원"
                  value={`${crew.memberCount}/${crew.maxMembers}명`}
                />
                <Stat icon={<Coins className="size-4" />} label="벌금" value={formatPoints(crew.penaltyPoints)} />
              </dl>
            </section>

            <div className="mt-auto flex flex-col gap-3 pt-8">
              {joinError && <Alert>{joinError}</Alert>}
              {crew.alreadyJoined ? (
                <Button onClick={() => navigate(`/crews/${crew.crewId}`)}>이미 참여한 크루예요 · 보러 가기</Button>
              ) : crew.memberCount >= crew.maxMembers ? (
                <Button disabled>정원이 가득 찼어요</Button>
              ) : (
                <Button onClick={onJoin} loading={joinCrew.isPending}>
                  크루 참여하기
                </Button>
              )}
            </div>
          </>
        )}
      </main>
    </div>
  )
}

function Stat({ icon, label, value }: { icon: ReactNode; label: string; value: string }) {
  return (
    <div className="rounded-2xl bg-stone-50 px-2 py-3">
      <dt className="flex items-center justify-center gap-1 text-xs text-stone-500">
        <span className="text-stone-400" aria-hidden>
          {icon}
        </span>
        {label}
      </dt>
      <dd className="mt-1 text-[15px] font-semibold">{value}</dd>
    </div>
  )
}
