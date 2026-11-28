import clsx from 'clsx'
import { Bell, Heart, MessageCircle, Receipt, UserPlus } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { formatRelative } from '../lib/format'
import { useInbox, useMarkAllRead, useRefreshInbox, type NotificationType } from '../lib/notifications'

const ICONS: Record<NotificationType, { icon: typeof Bell; className: string }> = {
  COMMENT: { icon: MessageCircle, className: 'bg-violet-100 text-violet-700' },
  LIKE: { icon: Heart, className: 'bg-rose-100 text-rose-600' },
  PENALTY: { icon: Receipt, className: 'bg-amber-100 text-amber-700' },
  MEMBER_JOINED: { icon: UserPlus, className: 'bg-lime-200 text-lime-800' },
}

/** 위쪽 메뉴의 종 모양 버튼. 누르면 최근 알림 목록이 펼쳐지고, 연 순간 모두 읽음이 된다 */
export function NotificationBell() {
  const [open, setOpen] = useState(false)
  const { data } = useInbox()
  const markAllRead = useMarkAllRead()
  const refresh = useRefreshInbox()
  const navigate = useNavigate()
  const root = useRef<HTMLDivElement>(null)
  const unread = data?.unreadCount ?? 0

  const close = () => {
    setOpen(false)
    void refresh()
  }

  useEffect(() => {
    if (!open) return
    const onPointer = (e: PointerEvent) => {
      if (root.current && !root.current.contains(e.target as Node)) close()
    }
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && close()
    document.addEventListener('pointerdown', onPointer)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('pointerdown', onPointer)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  return (
    <div ref={root} className="relative">
      <button
        type="button"
        onClick={() => {
          if (open) return close()
          setOpen(true)
          if (unread > 0) markAllRead.mutate()
        }}
        aria-label={unread > 0 ? `알림 ${unread}개 안 읽음` : '알림'}
        aria-expanded={open}
        className={clsx(
          'relative flex size-9 items-center justify-center rounded-lg transition',
          open ? 'bg-ink text-lime-400' : 'text-stone-500 hover:bg-stone-100 hover:text-ink',
        )}
      >
        <Bell className="size-4" />
        {unread > 0 && (
          <span
            data-testid="notification-badge"
            className="absolute -top-0.5 -right-0.5 flex h-4.5 min-w-4.5 items-center justify-center rounded-full border-2 border-white bg-coral-600 px-1 text-[10px] leading-none font-black text-white"
          >
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>

      {open && (
        <div
          role="dialog"
          aria-label="알림"
          className="fixed inset-x-3 top-16 z-30 max-h-[70dvh] overflow-hidden rounded-xl border-2 border-ink bg-white shadow-hard sm:absolute sm:inset-x-auto sm:top-11 sm:right-0 sm:w-96"
        >
          <p className="border-b-2 border-ink px-4 py-3 text-sm font-black">알림</p>
          <div className="max-h-[calc(70dvh-3rem)] overflow-y-auto">
            {!data ? (
              <p className="px-4 py-8 text-center text-sm text-stone-500">불러오는 중…</p>
            ) : data.items.length === 0 ? (
              <p className="px-4 py-10 text-center text-sm text-stone-500">
                아직 알림이 없어요.
                <br />
                댓글·좋아요·벌금·새 크루원 소식이 여기에 모여요.
              </p>
            ) : (
              <ul>
                {data.items.map((n) => {
                  const { icon: Icon, className } = ICONS[n.type]
                  return (
                    <li key={n.id} className="border-b border-stone-100 last:border-0">
                      <button
                        type="button"
                        onClick={() => {
                          close()
                          navigate(n.url)
                        }}
                        className={clsx(
                          'flex w-full items-start gap-3 px-4 py-3 text-left transition hover:bg-stone-50',
                          !n.read && 'bg-lime-50',
                        )}
                      >
                        <span className={clsx('mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-lg', className)}>
                          <Icon className="size-4" aria-hidden />
                        </span>
                        <span className="min-w-0 flex-1">
                          <span className="flex items-center gap-1.5 text-sm font-extrabold">
                            {n.title}
                            {!n.read && <span className="size-1.5 rounded-full bg-coral-600" aria-label="새 알림" />}
                          </span>
                          <span className="mt-0.5 block truncate text-sm text-stone-600">{n.body}</span>
                          <span className="mt-0.5 block text-xs text-stone-400">{formatRelative(n.createdAt)}</span>
                        </span>
                      </button>
                    </li>
                  )
                })}
              </ul>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
