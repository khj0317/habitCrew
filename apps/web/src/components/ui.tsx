import clsx from 'clsx'
import { ChevronLeft, Loader2 } from 'lucide-react'
import {
  useEffect,
  useId,
  useRef,
  type ButtonHTMLAttributes,
  type InputHTMLAttributes,
  type ReactNode,
  type TextareaHTMLAttributes,
} from 'react'
import { useNavigate } from 'react-router'
import { WEEK } from '../lib/format'
import type { DayOfWeek } from '../lib/types'

export function Logo({ className }: { className?: string }) {
  return (
    <span className={clsx('inline-flex items-center gap-2 font-bold tracking-tight', className)}>
      <svg viewBox="0 0 32 32" className="size-7" aria-hidden>
        <rect width="32" height="32" rx="8" fill="var(--color-brand-600)" />
        <path
          d="M9 16.5l4.5 4.5L23 11.5"
          fill="none"
          stroke="#fff"
          strokeWidth="3.2"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
      HabitCrew
    </span>
  )
}

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
  loading?: boolean
}

export function Button({ variant = 'primary', loading, disabled, className, children, ...rest }: ButtonProps) {
  return (
    <button
      {...rest}
      disabled={disabled || loading}
      className={clsx(
        'inline-flex h-12 items-center justify-center gap-2 rounded-xl px-5 text-[15px] font-semibold transition',
        'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600',
        'disabled:cursor-not-allowed disabled:opacity-60',
        variant === 'primary' && 'bg-brand-600 text-white hover:bg-brand-700 active:scale-[0.99]',
        variant === 'secondary' && 'border border-stone-200 bg-white text-stone-800 hover:bg-stone-50',
        variant === 'ghost' && 'text-stone-600 hover:bg-stone-100',
        variant === 'danger' && 'bg-red-600 text-white hover:bg-red-700',
        className,
      )}
    >
      {loading && <Loader2 className="size-4 animate-spin" aria-hidden />}
      {children}
    </button>
  )
}

type FieldProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string
  error?: string
  hint?: string
}

export function Field({ label, error, hint, className, ...rest }: FieldProps) {
  const id = useId()
  const describedBy = error || hint ? `${id}-desc` : undefined
  return (
    <div className={clsx('flex flex-col gap-1.5', className)}>
      <label htmlFor={id} className="text-sm font-medium text-stone-700">
        {label}
      </label>
      <input
        {...rest}
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={clsx(
          'h-12 rounded-xl border bg-white px-4 text-[15px] outline-none transition placeholder:text-stone-400',
          'focus:border-brand-600 focus:ring-4 focus:ring-brand-100',
          error ? 'border-red-400' : 'border-stone-200',
        )}
      />
      {(error || hint) && (
        <p id={describedBy} className={clsx('text-[13px]', error ? 'text-red-600' : 'text-stone-500')}>
          {error ?? hint}
        </p>
      )}
    </div>
  )
}

type TextAreaFieldProps = TextareaHTMLAttributes<HTMLTextAreaElement> & { label: string; error?: string }

export function TextAreaField({ label, error, className, value, maxLength, ...rest }: TextAreaFieldProps) {
  const id = useId()
  const length = typeof value === 'string' ? value.length : 0
  return (
    <div className={clsx('flex flex-col gap-1.5', className)}>
      <div className="flex items-baseline justify-between">
        <label htmlFor={id} className="text-sm font-medium text-stone-700">
          {label}
        </label>
        {maxLength && (
          <span className="text-xs text-stone-400">
            {length}/{maxLength}
          </span>
        )}
      </div>
      <textarea
        {...rest}
        id={id}
        value={value}
        maxLength={maxLength}
        aria-invalid={error ? true : undefined}
        className={clsx(
          'min-h-24 resize-none rounded-xl border bg-white px-4 py-3 text-[15px] outline-none transition placeholder:text-stone-400',
          'focus:border-brand-600 focus:ring-4 focus:ring-brand-100',
          error ? 'border-red-400' : 'border-stone-200',
        )}
      />
      {error && <p className="text-[13px] text-red-600">{error}</p>}
    </div>
  )
}

/** 인증 요일 고르기: 버튼 7개를 토글한다 */
export function DayPicker({
  value,
  onChange,
  error,
}: {
  value: DayOfWeek[]
  onChange: (days: DayOfWeek[]) => void
  error?: string
}) {
  const selected = new Set(value)
  const toggle = (day: DayOfWeek) => {
    const next = new Set(selected)
    if (next.has(day)) next.delete(day)
    else next.add(day)
    onChange(WEEK.map((d) => d.value).filter((d) => next.has(d)))
  }
  return (
    <fieldset className="flex flex-col gap-1.5">
      <legend className="mb-1.5 text-sm font-medium text-stone-700">인증 요일</legend>
      <div className="grid grid-cols-7 gap-1.5">
        {WEEK.map((day) => {
          const on = selected.has(day.value)
          return (
            <button
              key={day.value}
              type="button"
              aria-pressed={on}
              onClick={() => toggle(day.value)}
              className={clsx(
                'h-11 rounded-xl text-sm font-semibold transition',
                on ? 'bg-brand-600 text-white' : 'border border-stone-200 bg-white text-stone-500 hover:bg-stone-50',
                !on && (day.value === 'SATURDAY' || day.value === 'SUNDAY') && 'text-stone-400',
              )}
            >
              {day.label}
            </button>
          )
        })}
      </div>
      {error && <p className="text-[13px] text-red-600">{error}</p>}
    </fieldset>
  )
}

/** 상단 바: 뒤로 가기 + 제목 + 오른쪽 버튼 */
export function PageHeader({ title, back = '/', right }: { title?: string; back?: string; right?: ReactNode }) {
  const navigate = useNavigate()
  return (
    <header className="sticky top-0 z-10 flex h-14 items-center gap-1 bg-stone-50/90 px-2 backdrop-blur">
      <button
        onClick={() => navigate(back)}
        aria-label="뒤로 가기"
        className="flex size-10 items-center justify-center rounded-full text-stone-700 hover:bg-stone-100"
      >
        <ChevronLeft className="size-6" />
      </button>
      <h1 className="flex-1 truncate text-[17px] font-semibold">{title}</h1>
      {right}
    </header>
  )
}

/** 되돌리기 어려운 동작 전에 한 번 더 묻는 창 */
export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  danger,
  loading,
  error,
  onConfirm,
  onClose,
}: {
  open: boolean
  title: string
  description?: ReactNode
  confirmLabel: string
  danger?: boolean
  loading?: boolean
  error?: string | null
  onConfirm: () => void
  onClose: () => void
}) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      onClick={(e) => e.target === ref.current && onClose()}
      className="m-auto w-[calc(100%-2.5rem)] max-w-sm rounded-2xl bg-white p-0 shadow-xl backdrop:bg-black/40"
    >
      <div className="p-6">
        <h2 className="text-lg font-bold">{title}</h2>
        {description && <div className="mt-2 text-sm leading-relaxed text-stone-600">{description}</div>}
        {error && (
          <div className="mt-4">
            <Alert>{error}</Alert>
          </div>
        )}
        <div className="mt-6 grid grid-cols-2 gap-2">
          <Button variant="secondary" onClick={onClose} disabled={loading}>
            취소
          </Button>
          <Button variant={danger ? 'danger' : 'primary'} onClick={onConfirm} loading={loading}>
            {confirmLabel}
          </Button>
        </div>
      </div>
    </dialog>
  )
}

/** 닉네임 첫 글자 아바타 (사람마다 같은 색이 나오게 id로 색을 고른다) */
const AVATAR_COLORS = [
  'bg-brand-100 text-brand-700',
  'bg-sky-100 text-sky-700',
  'bg-violet-100 text-violet-700',
  'bg-rose-100 text-rose-700',
  'bg-amber-100 text-amber-700',
  'bg-teal-100 text-teal-700',
]

export function Avatar({
  userId,
  nickname,
  size = 'md',
  dimmed,
}: {
  userId: number
  nickname: string
  size?: 'sm' | 'md'
  dimmed?: boolean
}) {
  return (
    <span
      aria-hidden
      className={clsx(
        'flex shrink-0 items-center justify-center rounded-full font-bold',
        size === 'sm' ? 'size-8 text-xs' : 'size-10 text-sm',
        dimmed ? 'bg-stone-100 text-stone-400' : AVATAR_COLORS[userId % AVATAR_COLORS.length],
      )}
    >
      {nickname.slice(0, 1)}
    </span>
  )
}

export function ErrorState({ message, action }: { message: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center px-6 py-20 text-center">
      <div className="text-4xl">🧭</div>
      <p className="mt-4 text-stone-600">{message}</p>
      {action && <div className="mt-6">{action}</div>}
    </div>
  )
}

export function Alert({ children }: { children: ReactNode }) {
  return (
    <div role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">
      {children}
    </div>
  )
}

export function FullScreenSpinner() {
  return (
    <div className="flex min-h-dvh items-center justify-center" aria-label="불러오는 중">
      <Loader2 className="size-6 animate-spin text-brand-600" />
    </div>
  )
}
