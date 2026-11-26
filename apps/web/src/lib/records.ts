import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api } from './api'
import type { CalendarMonth, Ranking } from './types'

export const recordKeys = {
  ranking: (crewId: number, month: string) => ['crews', crewId, 'ranking', month] as const,
  calendar: (crewId: number, month: string, userId: number | null) =>
    ['crews', crewId, 'calendar', month, userId] as const,
}

/** "2026-11" 형식. 브라우저 시간대가 아니라 한국 시간 기준 이번 달 */
export function currentMonth(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit' })
    .format(new Date())
    .slice(0, 7)
}

export function shiftMonth(month: string, delta: number): string {
  const [y, m] = month.split('-').map(Number)
  const date = new Date(y, m - 1 + delta, 1)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

export function formatMonth(month: string): string {
  const [y, m] = month.split('-').map(Number)
  return `${y}년 ${m}월`
}

export function useRanking(crewId: number, month: string) {
  return useQuery({
    queryKey: recordKeys.ranking(crewId, month),
    queryFn: () => api<Ranking>(`/api/crews/${crewId}/ranking?month=${month}`),
    placeholderData: keepPreviousData, // 달을 넘길 때 화면이 비지 않게
  })
}

export function useCalendar(crewId: number, month: string, userId: number | null) {
  return useQuery({
    queryKey: recordKeys.calendar(crewId, month, userId),
    queryFn: () => api<CalendarMonth>(`/api/crews/${crewId}/calendar?month=${month}${userId ? `&userId=${userId}` : ''}`),
    placeholderData: keepPreviousData,
  })
}
