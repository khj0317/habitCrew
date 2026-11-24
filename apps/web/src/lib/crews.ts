import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './api'
import type { CrewDetail, CrewSummary, DayOfWeek, InvitePreview } from './types'

export const crewKeys = {
  all: ['crews'] as const,
  detail: (id: number) => ['crews', id] as const,
  invite: (code: string) => ['invites', code] as const,
}

export type CreateCrewInput = {
  name: string
  description: string
  certDays: DayOfWeek[]
  penaltyPoints: number
  maxMembers: number
}

export type UpdateCrewInput = { name: string; description: string; maxMembers: number }

export function useMyCrews() {
  return useQuery({ queryKey: crewKeys.all, queryFn: () => api<CrewSummary[]>('/api/crews') })
}

export function useCrew(id: number) {
  return useQuery({
    queryKey: crewKeys.detail(id),
    queryFn: () => api<CrewDetail>(`/api/crews/${id}`),
    enabled: Number.isFinite(id),
  })
}

export function useInvitePreview(code: string | null) {
  return useQuery({
    queryKey: crewKeys.invite(code ?? ''),
    queryFn: () => api<InvitePreview>(`/api/crews/invites/${encodeURIComponent(code!)}`),
    enabled: !!code,
    retry: false,
  })
}

/** 크루 상세를 돌려주는 변경 요청: 응답으로 상세 캐시를 바로 채우고 목록은 다시 불러온다 */
function useCrewMutation<TInput>(request: (input: TInput) => Promise<CrewDetail>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: request,
    onSuccess: (crew) => {
      queryClient.setQueryData(crewKeys.detail(crew.id), crew)
      queryClient.invalidateQueries({ queryKey: crewKeys.all, exact: true })
    },
  })
}

export function useCreateCrew() {
  return useCrewMutation((input: CreateCrewInput) => api<CrewDetail>('/api/crews', { method: 'POST', body: input }))
}

export function useJoinCrew() {
  return useCrewMutation((inviteCode: string) =>
    api<CrewDetail>('/api/crews/join', { method: 'POST', body: { inviteCode } }),
  )
}

export function useUpdateCrew(id: number) {
  return useCrewMutation((input: UpdateCrewInput) =>
    api<CrewDetail>(`/api/crews/${id}`, { method: 'PATCH', body: input }),
  )
}

export function useRegenerateInviteCode(id: number) {
  return useCrewMutation(() => api<CrewDetail>(`/api/crews/${id}/invite-code`, { method: 'POST' }))
}

export function useKickMember(id: number) {
  return useCrewMutation((userId: number) => api<CrewDetail>(`/api/crews/${id}/members/${userId}`, { method: 'DELETE' }))
}

export function useTransferOwner(id: number) {
  return useCrewMutation((userId: number) =>
    api<CrewDetail>(`/api/crews/${id}/owner`, { method: 'POST', body: { userId } }),
  )
}

export function useLeaveCrew(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => api<void>(`/api/crews/${id}/members/me`, { method: 'DELETE' }),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: crewKeys.detail(id) })
      queryClient.invalidateQueries({ queryKey: crewKeys.all, exact: true })
    },
  })
}
