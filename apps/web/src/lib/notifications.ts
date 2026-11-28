import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './api'

export type NotificationType = 'COMMENT' | 'LIKE' | 'PENALTY' | 'MEMBER_JOINED'

export interface AppNotification {
  id: number
  type: NotificationType
  title: string
  body: string
  url: string
  createdAt: string
  read: boolean
}

export interface Inbox {
  unreadCount: number
  items: AppNotification[]
}

const inboxKey = ['notifications'] as const

/** 알림함. 열어 두지 않아도 1분마다 새 알림 개수를 확인한다 (창이 뒤에 있으면 쉬고, 돌아오면 바로 확인) */
export function useInbox() {
  return useQuery({
    queryKey: inboxKey,
    queryFn: () => api<Inbox>('/api/notifications'),
    refetchInterval: 60_000,
  })
}

/**
 * 알림함을 열면 모두 읽음으로 바꾼다.
 * 배지 숫자는 바로 0으로 만들되, 목록의 "새 알림" 표시는 열려 있는 동안 남겨 두고 닫을 때 새로 받아온다
 */
export function useMarkAllRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => api<void>('/api/notifications/read', { method: 'POST' }),
    onSuccess: () =>
      queryClient.setQueryData<Inbox>(inboxKey, (old) => (old ? { ...old, unreadCount: 0 } : old)),
  })
}

export function useRefreshInbox() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: inboxKey })
}
