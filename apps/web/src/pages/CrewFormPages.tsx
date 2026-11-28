import clsx from 'clsx'
import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router'
import { Alert, Button, DayPicker, ErrorState, Field, PageHeader, TextAreaField } from '../components/ui'
import { ApiError } from '../lib/api'
import { useCreateCrew, useCrew, useUpdateCrew } from '../lib/crews'
import { formatPoints } from '../lib/format'
import type { DayOfWeek } from '../lib/types'

const PENALTY_PRESETS = [0, 500, 1000, 3000, 5000]

function toFieldErrors(err: unknown): { fields: Record<string, string>; message: string | null } {
  if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) {
    return { fields: err.fieldErrors, message: null }
  }
  return { fields: {}, message: err instanceof ApiError ? err.message : '저장하지 못했어요.' }
}

export function CreateCrewPage() {
  const navigate = useNavigate()
  const createCrew = useCreateCrew()
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [certDays, setCertDays] = useState<DayOfWeek[]>(['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'])
  const [penaltyPoints, setPenaltyPoints] = useState(1000)
  const [maxMembers, setMaxMembers] = useState(10)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState<string | null>(null)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setErrors({})
    setMessage(null)
    if (certDays.length === 0) {
      setErrors({ certDays: '인증 요일을 하나 이상 골라 주세요.' })
      return
    }
    try {
      const crew = await createCrew.mutateAsync({ name, description, certDays, penaltyPoints, maxMembers })
      navigate(`/crews/${crew.id}`, { replace: true, state: { created: true } })
    } catch (err) {
      const { fields, message } = toFieldErrors(err)
      setErrors(fields)
      setMessage(message)
    }
  }

  return (
    <div className="page-narrow">
      <PageHeader title="크루 만들기" />
      <form onSubmit={onSubmit} className="flex flex-col gap-6 px-5 pt-2 pb-10" noValidate>
        <Field
          label="크루 이름"
          placeholder="예) 아침 6시 기상 크루"
          value={name}
          onChange={(e) => setName(e.target.value)}
          error={errors.name}
          maxLength={30}
          required
        />
        <TextAreaField
          label="소개 (선택)"
          placeholder="어떤 습관을 함께 만들지 적어 주세요"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          error={errors.description}
          maxLength={200}
        />
        <DayPicker value={certDays} onChange={setCertDays} error={errors.certDays} />

        <fieldset className="flex flex-col gap-1.5">
          <legend className="mb-1.5 text-sm font-medium text-stone-700">인증을 빼먹으면 쌓이는 벌금</legend>
          <div className="flex flex-wrap gap-2">
            {PENALTY_PRESETS.map((points) => (
              <button
                key={points}
                type="button"
                aria-pressed={penaltyPoints === points}
                onClick={() => setPenaltyPoints(points)}
                className={clsx(
                  'h-10 rounded-full px-4 text-sm font-medium transition',
                  penaltyPoints === points
                    ? 'bg-stone-900 text-white'
                    : 'border border-stone-200 bg-white text-stone-600 hover:bg-stone-50',
                )}
              >
                {points === 0 ? '없음' : formatPoints(points)}
              </button>
            ))}
          </div>
          <p className="text-[13px] text-stone-500">실제 돈이 아닌 포인트예요. 크루 안에서 랭킹과 기록에만 쓰여요.</p>
          {errors.penaltyPoints && <p className="text-[13px] text-red-600">{errors.penaltyPoints}</p>}
        </fieldset>

        <MaxMembersStepper value={maxMembers} onChange={setMaxMembers} error={errors.maxMembers} />

        <div className="rounded-xl bg-amber-50 px-4 py-3 text-[13px] leading-relaxed text-amber-800">
          인증 요일과 벌금은 크루를 만든 뒤에는 바꿀 수 없어요. 정산 도중 규칙이 바뀌면 크루원끼리 헷갈리기 때문이에요.
        </div>

        {message && <Alert>{message}</Alert>}
        <Button type="submit" loading={createCrew.isPending}>
          크루 만들기
        </Button>
      </form>
    </div>
  )
}

function MaxMembersStepper({
  value,
  onChange,
  min = 2,
  error,
}: {
  value: number
  onChange: (value: number) => void
  min?: number
  error?: string
}) {
  const clamp = (n: number) => Math.min(30, Math.max(min, n))
  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-center justify-between">
        <span id="max-members-label" className="text-sm font-medium text-stone-700">
          정원
        </span>
        <div className="flex items-center gap-1" role="group" aria-labelledby="max-members-label">
          <button
            type="button"
            aria-label="정원 줄이기"
            onClick={() => onChange(clamp(value - 1))}
            disabled={value <= min}
            className="size-10 rounded-full border border-stone-200 bg-white text-lg text-stone-600 disabled:opacity-40"
          >
            −
          </button>
          <span className="w-14 text-center font-semibold tabular-nums" aria-live="polite">
            {value}명
          </span>
          <button
            type="button"
            aria-label="정원 늘리기"
            onClick={() => onChange(clamp(value + 1))}
            disabled={value >= 30}
            className="size-10 rounded-full border border-stone-200 bg-white text-lg text-stone-600 disabled:opacity-40"
          >
            +
          </button>
        </div>
      </div>
      {error && <p className="text-[13px] text-red-600">{error}</p>}
    </div>
  )
}

export function EditCrewPage() {
  const id = Number(useParams().crewId)
  const navigate = useNavigate()
  const { data: crew, isPending, isError } = useCrew(id)
  const updateCrew = useUpdateCrew(id)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [maxMembers, setMaxMembers] = useState(10)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState<string | null>(null)

  useEffect(() => {
    if (!crew) return
    setName(crew.name)
    setDescription(crew.description ?? '')
    setMaxMembers(crew.maxMembers)
  }, [crew])

  if (isPending) return <div className="page-narrow"><PageHeader back={`/crews/${id}`} /></div>
  if (isError || crew.myRole !== 'OWNER') {
    return (
      <div className="page-narrow">
        <PageHeader back="/" />
        <ErrorState message="크루장만 크루 정보를 바꿀 수 있어요." />
      </div>
    )
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setErrors({})
    setMessage(null)
    try {
      await updateCrew.mutateAsync({ name, description, maxMembers })
      navigate(`/crews/${id}`, { replace: true })
    } catch (err) {
      const { fields, message } = toFieldErrors(err)
      setErrors(fields)
      setMessage(message)
    }
  }

  return (
    <div className="page-narrow">
      <PageHeader title="크루 정보 수정" back={`/crews/${id}`} />
      <form onSubmit={onSubmit} className="flex flex-col gap-6 px-5 pt-2 pb-10" noValidate>
        <Field
          label="크루 이름"
          value={name}
          onChange={(e) => setName(e.target.value)}
          error={errors.name}
          maxLength={30}
        />
        <TextAreaField
          label="소개 (선택)"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          error={errors.description}
          maxLength={200}
        />
        <MaxMembersStepper
          value={maxMembers}
          onChange={setMaxMembers}
          min={Math.max(2, crew.members.length)}
          error={errors.maxMembers}
        />
        <p className="-mt-4 text-[13px] text-stone-500">지금 인원({crew.members.length}명)보다 적게 줄일 수는 없어요.</p>
        {message && <Alert>{message}</Alert>}
        <Button type="submit" loading={updateCrew.isPending}>
          저장하기
        </Button>
      </form>
    </div>
  )
}
