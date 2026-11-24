import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { api } from './api'
import type { CrewStats, SettlementHistory } from './types'

export const statsKeys = {
  stats: (crewId: number) => ['crews', crewId, 'stats'] as const,
  history: (crewId: number) => ['crews', crewId, 'settlements'] as const,
}

export function useCrewStats(crewId: number) {
  return useQuery({
    queryKey: statsKeys.stats(crewId),
    queryFn: () => api<CrewStats>(`/api/crews/${crewId}/stats`),
  })
}

export function useSettlementHistory(crewId: number) {
  return useInfiniteQuery({
    queryKey: statsKeys.history(crewId),
    queryFn: ({ pageParam }) =>
      api<SettlementHistory>(`/api/crews/${crewId}/settlements${pageParam ? `?before=${pageParam}` : ''}`),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextBefore,
  })
}
