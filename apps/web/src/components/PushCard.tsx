import clsx from 'clsx'
import { Bell, BellOff, Share } from 'lucide-react'
import { usePush } from '../lib/push'
import { Alert } from './ui'

/** 인증 마감 알림 켜기/끄기 (기기마다 따로) */
export function PushCard() {
  const { state, busy, error, enable, disable, sendTest } = usePush()
  if (state === 'loading' || state === 'unsupported') return null

  const on = state === 'on'
  return (
    <section className="rounded-2xl border border-stone-200 bg-white px-4 py-4">
      <div className="flex items-center gap-3">
        <span
          className={clsx(
            'flex size-10 shrink-0 items-center justify-center rounded-xl',
            on ? 'bg-brand-50 text-brand-600' : 'bg-stone-100 text-stone-400',
          )}
          aria-hidden
        >
          {on ? <Bell className="size-5" /> : <BellOff className="size-5" />}
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold">인증 마감 알림</p>
          <p className="text-xs text-stone-500">
            {state === 'denied'
              ? '브라우저에서 알림이 차단돼 있어요. 주소창 옆 자물쇠 → 알림에서 허용해 주세요.'
              : state === 'ios-install'
                ? '아이폰은 홈 화면에 추가한 앱에서 알림을 받을 수 있어요.'
                : '인증을 안 한 날 밤 10시에 알려 드려요 (이 기기)'}
          </p>
        </div>
        {(state === 'on' || state === 'off') && (
          <button
            role="switch"
            aria-checked={on}
            aria-label="인증 마감 알림"
            disabled={busy}
            onClick={on ? disable : enable}
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
        )}
      </div>
      {state === 'ios-install' && (
        <p className="mt-3 inline-flex items-center gap-1 rounded-lg bg-stone-50 px-3 py-2 text-xs text-stone-600">
          Safari 아래쪽 <Share className="size-3.5" aria-label="공유" /> 버튼 → "홈 화면에 추가"
        </p>
      )}
      {on && (
        <button onClick={sendTest} className="mt-3 text-xs font-medium text-brand-700 hover:underline">
          시험 알림 보내기
        </button>
      )}
      {error && (
        <div className="mt-3">
          <Alert>{error}</Alert>
        </div>
      )}
    </section>
  )
}
