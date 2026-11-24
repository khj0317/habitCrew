import { useState, type ChangeEvent, type FormEvent, type ReactNode } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { Alert, Button, Field, Logo } from '../components/ui'
import { ApiError } from '../lib/api'
import { useAuth } from '../providers/authContext'

function AuthLayout({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-md flex-col px-6 py-10">
      <Logo className="text-lg text-stone-900" />
      <div className="mt-12 mb-8">
        <h1 className="text-2xl font-bold leading-snug">{title}</h1>
        <p className="mt-2 text-stone-500">{subtitle}</p>
      </div>
      {children}
    </main>
  )
}

/** 로그인 후 원래 가려던 페이지로 돌려보낸다 (외부 주소로는 보내지 않음) */
function useRedirectTarget() {
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from
  return from && from.startsWith('/') && !from.startsWith('//') ? from : '/'
}

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const target = useRedirectTarget()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

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
        <Button type="submit" loading={submitting} className="mt-2">
          로그인
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-stone-500">
        아직 계정이 없나요?{' '}
        <Link to="/signup" className="font-semibold text-brand-700 hover:underline">
          회원가입
        </Link>
      </p>
    </AuthLayout>
  )
}

export function SignupPage() {
  const { signup } = useAuth()
  const navigate = useNavigate()
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
      navigate('/', { replace: true })
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
        <Link to="/login" className="font-semibold text-brand-700 hover:underline">
          로그인
        </Link>
      </p>
    </AuthLayout>
  )
}
