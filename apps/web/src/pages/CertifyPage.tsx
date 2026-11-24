import { Camera, ImagePlus, RefreshCw } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { Alert, Button, PageHeader, TextAreaField } from '../components/ui'
import { ApiError } from '../lib/api'
import { useCreateCertification } from '../lib/certifications'
import { useCrew } from '../lib/crews'
import { formatBytes, resizeForUpload } from '../lib/image'

type Prepared = { blob: Blob; previewUrl: string; originalSize: number }

export function CertifyPage() {
  const crewId = Number(useParams().crewId)
  const navigate = useNavigate()
  const { data: crew } = useCrew(crewId)
  const create = useCreateCertification(crewId)
  const inputRef = useRef<HTMLInputElement>(null)
  const [photo, setPhoto] = useState<Prepared | null>(null)
  const [preparing, setPreparing] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState<string | null>(null)

  // 미리보기용 주소는 다 쓰면 풀어 준다 (메모리 누수 방지)
  useEffect(() => () => {
    if (photo) URL.revokeObjectURL(photo.previewUrl)
  }, [photo])

  async function onPick(file: File | undefined) {
    if (!file) return
    setError(null)
    setPreparing(true)
    try {
      const blob = await resizeForUpload(file)
      setPhoto({ blob, previewUrl: URL.createObjectURL(blob), originalSize: file.size })
    } catch (err) {
      setError(err instanceof Error ? err.message : '사진을 준비하지 못했어요.')
    } finally {
      setPreparing(false)
      if (inputRef.current) inputRef.current.value = '' // 같은 사진을 다시 골라도 onChange가 오게
    }
  }

  async function onSubmit() {
    if (!photo) return
    setError(null)
    try {
      await create.mutateAsync({ photo: photo.blob, message })
      navigate(`/crews/${crewId}`, { replace: true, state: { certified: true } })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '인증을 올리지 못했어요. 잠시 후 다시 시도해 주세요.')
    }
  }

  return (
    <div className="mx-auto flex min-h-dvh w-full max-w-md flex-col">
      <PageHeader title={crew ? `${crew.name} 인증` : '인증하기'} back={`/crews/${crewId}`} />
      <main className="flex flex-1 flex-col gap-5 px-5 pb-8">
        <input
          ref={inputRef}
          type="file"
          // capture 속성을 쓰면 안드로이드에서 앨범을 고를 수 없어서 빼 둔다 (휴대폰이 "카메라/앨범" 선택 창을 띄운다)
          accept="image/*"
          className="sr-only"
          id="photo-input"
          onChange={(e) => onPick(e.target.files?.[0])}
        />

        {photo ? (
          <div className="relative overflow-hidden rounded-3xl bg-stone-100">
            <img src={photo.previewUrl} alt="올릴 인증 사진 미리보기" className="max-h-[60vh] w-full object-contain" />
            <button
              onClick={() => inputRef.current?.click()}
              className="absolute right-3 bottom-3 inline-flex items-center gap-1.5 rounded-full bg-black/60 px-3 py-2 text-sm font-medium text-white backdrop-blur hover:bg-black/70"
            >
              <RefreshCw className="size-4" aria-hidden />
              다시 고르기
            </button>
          </div>
        ) : (
          <label
            htmlFor="photo-input"
            className="flex aspect-[4/3] cursor-pointer flex-col items-center justify-center gap-3 rounded-3xl border-2 border-dashed border-stone-300 bg-white text-stone-500 transition hover:border-brand-500 hover:text-brand-700"
          >
            {preparing ? (
              <span className="text-sm">사진을 준비하는 중…</span>
            ) : (
              <>
                <span className="flex size-14 items-center justify-center rounded-2xl bg-brand-50 text-brand-600">
                  <Camera className="size-7" aria-hidden />
                </span>
                <span className="font-semibold text-stone-700">사진 찍기 또는 고르기</span>
                <span className="inline-flex items-center gap-1 text-xs">
                  <ImagePlus className="size-3.5" aria-hidden />
                  오늘 한 일을 보여 주는 사진 한 장
                </span>
              </>
            )}
          </label>
        )}

        {photo && (
          <p className="-mt-3 text-center text-xs text-stone-400">
            {formatBytes(photo.originalSize)} → {formatBytes(photo.blob.size)}로 줄여서 올려요 · 위치 정보는 지워져요
          </p>
        )}

        <TextAreaField
          label="한마디 (선택)"
          placeholder="오늘의 기록을 남겨 보세요"
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          maxLength={200}
        />

        {error && <Alert>{error}</Alert>}

        <Button className="mt-auto" onClick={onSubmit} disabled={!photo || preparing} loading={create.isPending}>
          인증 올리기
        </Button>
      </main>
    </div>
  )
}
