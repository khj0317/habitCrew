import clsx from 'clsx'
import { useState, type FormEvent, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { PushCard } from '../components/PushCard'
import { Alert, Button, ConfirmDialog, Field, PageHeader } from '../components/ui'
import { api, ApiError } from '../lib/api'
import type { TokenResponse, User } from '../lib/types'
import { useAuth } from '../providers/authContext'

/** 내 계정: 닉네임, 알림, 비밀번호, 탈퇴 */
export function SettingsPage() {
  const { user } = useAuth()
  if (!user) return null

  return (
    <div className="page-narrow pb-16">
      <PageHeader title="설정" />
      <main className="flex flex-col gap-5 px-5 lg:px-0">
        <section className="flex items-center justify-between rounded-2xl bg-white px-5 py-4 ring-1 ring-stone-200">
          <div>
            <p className="font-semibold">{user.nickname}</p>
            <p className="text-sm text-stone-500">{user.guest ? '체험 계정' : user.email}</p>
          </div>
        </section>

        {user.guest && (
          <div className="rounded-2xl bg-amber-50 px-4 py-3 text-sm leading-relaxed text-amber-800">
            체험 계정은 닉네임·비밀번호를 바꾸거나 탈퇴할 수 없어요. 24시간 뒤 자동으로 사라져요.
          </div>
        )}

        <Section title="알림">
          <PushCard />
          <ActivityToggle user={user} />
        </Section>

        {!user.guest && (
          <>
            <Section title="프로필">
              <NicknameForm user={user} />
            </Section>
            <Section title="비밀번호">
              <PasswordForm />
            </Section>
            <Section title="탈퇴">
              <DeleteAccount />
            </Section>
          </>
        )}
      </main>
    </div>
  )
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-3">
      <h2 className="px-1 text-sm font-semibold text-stone-500">{title}</h2>
      {children}
    </section>
  )
}

function Card({ children }: { children: ReactNode }) {
  return <div className="rounded-2xl bg-white px-5 py-5 ring-1 ring-stone-200">{children}</div>
}

function ActivityToggle({ user }: { user: User }) {
  const { updateUser } = useAuth()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const on = user.notifyActivity

  async function toggle() {
    setBusy(true)
    setError(null)
    try {
      updateUser(await api<User>('/api/users/me/notifications', { method: 'PUT', body: { activity: !on } }))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '바꾸지 못했어요.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="rounded-2xl bg-white px-4 py-4 ring-1 ring-stone-200">
      <div className="flex items-center gap-3">
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold">댓글·좋아요 알림</p>
          <p className="text-xs text-stone-500">내 인증에 크루원이 반응하면 알려 드려요 (좋아요는 인증마다 하루 한 번)</p>
        </div>
        <button
          role="switch"
          aria-checked={on}
          aria-label="댓글·좋아요 알림"
          disabled={busy}
          onClick={toggle}
          className={clsx(
            'relative h-7 w-12 shrink-0 rounded-full transition disabled:opacity-50',
            on ? 'bg-brand-600' : 'bg-stone-300',
          )}
        >
          <span
            className={clsx(
              'absolute top-0.5 left-0.5 size-6 rounded-full bg-white shadow transition-transform',
              on && 'translate-x-5',
            )}
          />
        </button>
      </div>
      {error && (
        <div className="mt-3">
          <Alert>{error}</Alert>
        </div>
      )}
    </div>
  )
}

function NicknameForm({ user }: { user: User }) {
  const { updateUser } = useAuth()
  const [nickname, setNickname] = useState(user.nickname)
  const [error, setError] = useState<string | undefined>()
  const [saved, setSaved] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(undefined)
    setSaved(false)
    setSubmitting(true)
    try {
      updateUser(await api<User>('/api/users/me', { method: 'PATCH', body: { nickname } }))
      setSaved(true)
    } catch (err) {
      setError(err instanceof ApiError ? (err.fieldErrors.nickname ?? err.message) : '바꾸지 못했어요.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Card>
      <form onSubmit={onSubmit} className="flex flex-col gap-3" noValidate>
        <Field
          label="닉네임"
          value={nickname}
          onChange={(e) => {
            setNickname(e.target.value)
            setSaved(false)
          }}
          maxLength={20}
          error={error}
          hint={saved ? '바꿨어요 ✓' : '한글, 영문, 숫자, 밑줄(_) 2~20자'}
        />
        <Button type="submit" variant="secondary" loading={submitting} disabled={nickname.trim() === user.nickname}>
          닉네임 저장
        </Button>
      </form>
    </Card>
  )
}

function PasswordForm() {
  const { applySession } = useAuth()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [errors, setErrors] = useState<{ current?: string; next?: string }>({})
  const [message, setMessage] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setErrors({})
    setMessage(null)
    setSubmitting(true)
    try {
      applySession(
        await api<TokenResponse>('/api/users/me/password', {
          method: 'PUT',
          body: { currentPassword: current, newPassword: next },
        }),
      )
      setCurrent('')
      setNext('')
      setMessage('비밀번호를 바꿨어요. 다른 기기에서는 모두 로그아웃됐어요.')
    } catch (err) {
      if (err instanceof ApiError && err.code === 'INVALID_CURRENT_PASSWORD') setErrors({ current: err.message })
      else if (err instanceof ApiError && err.fieldErrors.newPassword) setErrors({ next: err.fieldErrors.newPassword })
      else setErrors({ current: err instanceof ApiError ? err.message : '바꾸지 못했어요.' })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Card>
      <form onSubmit={onSubmit} className="flex flex-col gap-3" noValidate>
        <Field
          label="현재 비밀번호"
          type="password"
          autoComplete="current-password"
          value={current}
          onChange={(e) => setCurrent(e.target.value)}
          error={errors.current}
        />
        <Field
          label="새 비밀번호"
          type="password"
          autoComplete="new-password"
          value={next}
          onChange={(e) => setNext(e.target.value)}
          error={errors.next}
          hint="영문과 숫자를 섞어 8자 이상"
        />
        {message && (
          <p className="rounded-xl bg-brand-50 px-4 py-3 text-sm text-brand-700" role="status">
            {message}
          </p>
        )}
        <Button type="submit" variant="secondary" loading={submitting} disabled={!current || !next}>
          비밀번호 바꾸기
        </Button>
      </form>
    </Card>
  )
}

function DeleteAccount() {
  const { logout } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [deleting, setDeleting] = useState(false)

  async function onConfirm() {
    setError(null)
    setDeleting(true)
    try {
      await api('/api/users/me', { method: 'DELETE', body: { password } })
      await logout().catch(() => {})
      navigate('/login', { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '탈퇴하지 못했어요.')
    } finally {
      setDeleting(false)
    }
  }

  return (
    <Card>
      <p className="text-sm leading-relaxed text-stone-600">
        탈퇴하면 내 인증 사진·댓글·좋아요가 지워지고 다시 되돌릴 수 없어요. 크루원이 남아 있는 크루의 크루장이라면 먼저
        크루장을 넘겨 주세요.
      </p>
      <Button variant="danger" className="mt-4 w-full" onClick={() => setOpen(true)}>
        탈퇴하기
      </Button>
      <ConfirmDialog
        open={open}
        title="정말 탈퇴할까요?"
        description={
          <div className="flex flex-col gap-3">
            <p>되돌릴 수 없어요. 확인을 위해 비밀번호를 입력해 주세요.</p>
            <Field
              label="비밀번호"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>
        }
        confirmLabel="탈퇴"
        danger
        loading={deleting}
        error={error}
        onClose={() => {
          setOpen(false)
          setPassword('')
          setError(null)
        }}
        onConfirm={onConfirm}
      />
    </Card>
  )
}
