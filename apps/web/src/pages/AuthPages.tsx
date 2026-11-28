import { useState, type ChangeEvent, type FormEvent, type ReactNode } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { Alert, Button, Field, Logo } from '../components/ui'
import { ApiError } from '../lib/api'
import { useRedirectTarget } from '../lib/redirect'
import { useAuth } from '../providers/authContext'

/**
 * 로그인·회원가입 화면.
 * 휴대폰: 폼만 한 줄로. 데스크톱: 왼쪽에 서비스 소개, 오른쪽에 폼.
 */
function AuthLayout({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-2">
      <BrandPanel />
      <main className="mx-auto flex min-h-dvh w-full max-w-md flex-col px-6 py-10 lg:justify-center lg:py-16">
        <Logo className="text-lg text-stone-900 lg:hidden" />
        <div className="mt-12 mb-8 lg:mt-0">
          <h1 className="text-2xl font-bold leading-snug lg:text-3xl">{title}</h1>
          <p className="mt-2 text-stone-500">{subtitle}</p>
        </div>
        {children}
      </main>
    </div>
  )
}

const FEATURES = [
  { emoji: '📸', title: '사진 한 장으로 인증', text: '인증일마다 오늘 한 일을 사진으로 남겨요' },
  { emoji: '🔥', title: '연속 기록과 월간 랭킹', text: '달력에 쌓이는 잔디로 꾸준함이 보여요' },
  { emoji: '💸', title: '빼먹으면 벌금 포인트', text: '매일 자정, 크루 규칙대로 자동 정산돼요' },
  { emoji: '🔔', title: '마감 2시간 전 알림', text: '밤 10시, 아직 안 했다면 알려 드려요' },
]

function BrandPanel() {
  return (
    <aside className="relative hidden overflow-hidden bg-gradient-to-br from-brand-600 to-brand-700 px-14 py-14 text-white lg:flex lg:flex-col">
      <div
        className="pointer-events-none absolute -top-24 -right-24 size-96 rounded-full bg-white/10"
        aria-hidden
      />
      <div
        className="pointer-events-none absolute -bottom-32 -left-20 size-80 rounded-full bg-white/5"
        aria-hidden
      />
      <span className="relative inline-flex items-center gap-2 text-xl font-bold">
        <span className="flex size-9 items-center justify-center rounded-xl bg-white text-brand-600" aria-hidden>
          ✓
        </span>
        HabitCrew
      </span>
      <div className="relative my-auto max-w-md">
        <h2 className="text-4xl leading-tight font-bold">
          혼자서는 사흘,
          <br />
          크루와 함께라면 오래.
        </h2>
        <p className="mt-4 text-lg text-white/80">친구들과 매일 인증하며 습관을 만드는 서비스예요.</p>
        <ul className="mt-10 flex flex-col gap-5">
          {FEATURES.map((f) => (
            <li key={f.title} className="flex gap-4">
              <span className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-white/15 text-xl" aria-hidden>
                {f.emoji}
              </span>
              <div>
                <p className="font-semibold">{f.title}</p>
                <p className="text-sm text-white/75">{f.text}</p>
              </div>
            </li>
          ))}
        </ul>
      </div>
      <p className="relative text-sm text-white/60">포트폴리오 프로젝트 · Spring Boot · React</p>
    </aside>
  )
}

export function LoginPage() {
  const { login, startGuest } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const target = useRedirectTarget()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [startingGuest, setStartingGuest] = useState(false)

  async function onGuest() {
    setError(null)
    setStartingGuest(true)
    try {
      await startGuest()
      navigate('/', { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '체험 계정을 만들지 못했어요.')
    } finally {
      setStartingGuest(false)
    }
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await login(email, password)
      navigate(target, { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '로그인하지 못했어요.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout title={'다시 만나서 반가워요 👋'} subtitle="오늘의 인증, 크루가 기다리고 있어요.">
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        <Field
          label="이메일"
          type="email"
          autoComplete="email"
          placeholder="you@example.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
        <Field
          label="비밀번호"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
        {error && <Alert>{error}</Alert>}
        <Button type="submit" loading={submitting} disabled={startingGuest} className="mt-2">
          로그인
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-stone-500">
        아직 계정이 없나요?{' '}
        <Link to="/signup" state={location.state} className="font-semibold text-brand-700 hover:underline">
          회원가입
        </Link>
      </p>

      <div className="mt-10 flex items-center gap-3 text-xs text-stone-400" aria-hidden>
        <span className="h-px flex-1 bg-stone-200" />
        또는
        <span className="h-px flex-1 bg-stone-200" />
      </div>
      <Button variant="secondary" className="mt-4" onClick={onGuest} loading={startingGuest} disabled={submitting}>
        로그인 없이 둘러보기 👀
      </Button>
      <p className="mt-2 text-center text-xs leading-relaxed text-stone-400">
        2주치 기록이 채워진 체험 크루로 바로 시작해요. 체험 계정은 24시간 뒤 사라져요.
      </p>
    </AuthLayout>
  )
}

export function SignupPage() {
  const { signup } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const target = useRedirectTarget()
  const [form, setForm] = useState({ email: '', password: '', nickname: '' })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const update = (key: keyof typeof form) => (e: ChangeEvent<HTMLInputElement>) => {
    setForm((prev) => ({ ...prev, [key]: e.target.value }))
    setFieldErrors((prev) => ({ ...prev, [key]: '' }))
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setFieldErrors({})
    setSubmitting(true)
    try {
      await signup(form.email, form.password, form.nickname)
      navigate(target, { replace: true })
    } catch (err) {
      if (!(err instanceof ApiError)) {
        setError('회원가입하지 못했어요.')
      } else if (err.code === 'DUPLICATE_EMAIL') {
        setFieldErrors({ email: err.message })
      } else if (err.code === 'DUPLICATE_NICKNAME') {
        setFieldErrors({ nickname: err.message })
      } else if (Object.keys(err.fieldErrors).length > 0) {
        setFieldErrors(err.fieldErrors)
      } else {
        setError(err.message)
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout title={'함께하면 오래 가요 🌱'} subtitle="크루와 매일 인증하며 습관을 만들어 보세요.">
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        <Field
          label="이메일"
          type="email"
          autoComplete="email"
          placeholder="you@example.com"
          value={form.email}
          onChange={update('email')}
          error={fieldErrors.email || undefined}
          required
        />
        <Field
          label="비밀번호"
          type="password"
          autoComplete="new-password"
          value={form.password}
          onChange={update('password')}
          error={fieldErrors.password || undefined}
          hint="영문과 숫자를 섞어 8자 이상"
          required
        />
        <Field
          label="닉네임"
          autoComplete="nickname"
          placeholder="크루에게 보일 이름"
          value={form.nickname}
          onChange={update('nickname')}
          error={fieldErrors.nickname || undefined}
          hint="한글, 영문, 숫자, 밑줄(_) 2~20자"
          maxLength={20}
          required
        />
        {error && <Alert>{error}</Alert>}
        <Button type="submit" loading={submitting} className="mt-2">
          가입하고 시작하기
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-stone-500">
        이미 계정이 있나요?{' '}
        <Link to="/login" state={location.state} className="font-semibold text-brand-700 hover:underline">
          로그인
        </Link>
      </p>
    </AuthLayout>
  )
}
