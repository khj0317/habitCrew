import { useInfiniteQuery, useMutation, useQuery, useQueryClient, type InfiniteData } from '@tanstack/react-query'
import { api } from './api'
import type { Certification, Comment, Feed, TodayStatus } from './types'

export const certKeys = {
  today: (crewId: number) => ['crews', crewId, 'today'] as const,
  feed: (crewId: number) => ['crews', crewId, 'feed'] as const,
  detail: (id: number) => ['certifications', id] as const,
  comments: (id: number) => ['certifications', id, 'comments'] as const,
}

export function useToday(crewId: number) {
  return useQuery({
    queryKey: certKeys.today(crewId),
    queryFn: () => api<TodayStatus>(`/api/crews/${crewId}/certifications/today`),
  })
}

export function useFeed(crewId: number) {
  return useInfiniteQuery({
    queryKey: certKeys.feed(crewId),
    queryFn: ({ pageParam }) =>
      api<Feed>(`/api/crews/${crewId}/certifications${pageParam ? `?cursor=${pageParam}` : ''}`),
    initialPageParam: null as number | null,
    getNextPageParam: (last) => last.nextCursor,
    // 사진 주소(서명 URL)는 1시간 뒤 만료되므로, 그 전에 새로 받아 온다
    staleTime: 5 * 60_000,
    refetchInterval: 50 * 60_000,
  })
}

export function useCertification(id: number) {
  return useQuery({
    queryKey: certKeys.detail(id),
    queryFn: () => api<Certification>(`/api/certifications/${id}`),
  })
}

export function useComments(id: number) {
  return useQuery({
    queryKey: certKeys.comments(id),
    queryFn: () => api<Comment[]>(`/api/certifications/${id}/comments`),
  })
}

export function useCreateCertification(crewId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ photo, message }: { photo: Blob; message: string }) => {
      const form = new FormData()
      form.append('photo', photo, 'photo.jpg')
      if (message.trim()) form.append('message', message.trim())
      return api<Certification>(`/api/crews/${crewId}/certifications`, { method: 'POST', body: form })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: certKeys.today(crewId) })
      queryClient.invalidateQueries({ queryKey: certKeys.feed(crewId) })
      queryClient.invalidateQueries({ queryKey: ['crews', crewId, 'stats'] }) // 연속 인증이 늘어난다
    },
  })
}

export function useDeleteCertification(crewId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api<void>(`/api/certifications/${id}`, { method: 'DELETE' }),
    onSuccess: (_, id) => {
      queryClient.removeQueries({ queryKey: certKeys.detail(id) })
      queryClient.invalidateQueries({ queryKey: certKeys.today(crewId) })
      queryClient.invalidateQueries({ queryKey: certKeys.feed(crewId) })
      queryClient.invalidateQueries({ queryKey: ['crews', crewId, 'stats'] })
    },
  })
}

/** 피드 캐시와 상세 캐시에 있는 같은 인증을 한꺼번에 고친다 */
function patchCertification(
  queryClient: ReturnType<typeof useQueryClient>,
  crewId: number,
  id: number,
  patch: (c: Certification) => Certification,
) {
  queryClient.setQueryData<InfiniteData<Feed>>(certKeys.feed(crewId), (feed) =>
    feed && {
      ...feed,
      pages: feed.pages.map((page) => ({ ...page, items: page.items.map((c) => (c.id === id ? patch(c) : c)) })),
    },
  )
  queryClient.setQueryData<Certification>(certKeys.detail(id), (c) => c && patch(c))
}

/**
 * 좋아요: 누르는 즉시 화면에 반영하고(낙관적 업데이트), 서버 응답의 실제 개수로 맞춘다. 실패하면 되돌린다.
 * 서버는 PUT/DELETE라서 연타해도 결과가 같다.
 */
export function useToggleLike(crewId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, like }: { id: number; like: boolean }) =>
      api<{ liked: boolean; likeCount: number }>(`/api/certifications/${id}/like`, { method: like ? 'PUT' : 'DELETE' }),
    onMutate: async ({ id, like }) => {
      await queryClient.cancelQueries({ queryKey: certKeys.feed(crewId) })
      const previousFeed = queryClient.getQueryData(certKeys.feed(crewId))
      const previousDetail = queryClient.getQueryData(certKeys.detail(id))
      patchCertification(queryClient, crewId, id, (c) =>
        c.likedByMe === like ? c : { ...c, likedByMe: like, likeCount: c.likeCount + (like ? 1 : -1) },
      )
      return { previousFeed, previousDetail }
    },
    onError: (_err, { id }, context) => {
      queryClient.setQueryData(certKeys.feed(crewId), context?.previousFeed)
      queryClient.setQueryData(certKeys.detail(id), context?.previousDetail)
    },
    onSuccess: (result, { id }) => {
      patchCertification(queryClient, crewId, id, (c) => ({ ...c, likedByMe: result.liked, likeCount: result.likeCount }))
    },
  })
}

export function useAddComment(crewId: number, id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (content: string) =>
      api<Comment>(`/api/certifications/${id}/comments`, { method: 'POST', body: { content } }),
    onSuccess: (comment) => {
      queryClient.setQueryData<Comment[]>(certKeys.comments(id), (list) => [...(list ?? []), comment])
      patchCertification(queryClient, crewId, id, (c) => ({ ...c, commentCount: c.commentCount + 1 }))
    },
  })
}

export function useDeleteComment(crewId: number, id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (commentId: number) =>
      api<void>(`/api/certifications/${id}/comments/${commentId}`, { method: 'DELETE' }),
    onSuccess: (_, commentId) => {
      queryClient.setQueryData<Comment[]>(certKeys.comments(id), (list) => list?.filter((c) => c.id !== commentId))
      patchCertification(queryClient, crewId, id, (c) => ({ ...c, commentCount: Math.max(0, c.commentCount - 1) }))
    },
  })
}
