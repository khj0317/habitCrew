import type { DayOfWeek } from './types'

export const WEEK: { value: DayOfWeek; label: string }[] = [
  { value: 'MONDAY', label: '월' },
  { value: 'TUESDAY', label: '화' },
  { value: 'WEDNESDAY', label: '수' },
  { value: 'THURSDAY', label: '목' },
  { value: 'FRIDAY', label: '금' },
  { value: 'SATURDAY', label: '토' },
  { value: 'SUNDAY', label: '일' },
]

const WEEKDAYS: DayOfWeek[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY']
const WEEKEND: DayOfWeek[] = ['SATURDAY', 'SUNDAY']

/** ["MONDAY","WEDNESDAY"] → "월·수", 7일이면 "매일", 평일 5일이면 "평일" */
export function formatDays(days: DayOfWeek[]): string {
  const set = new Set(days)
  if (set.size === 7) return '매일'
  if (set.size === 5 && WEEKDAYS.every((d) => set.has(d))) return '평일'
  if (set.size === 2 && WEEKEND.every((d) => set.has(d))) return '주말'
  return WEEK.filter((d) => set.has(d.value))
    .map((d) => d.label)
    .join('·')
}

export function formatPoints(points: number): string {
  return `${points.toLocaleString('ko-KR')}P`
}

/** 초대 코드를 읽기 쉽게 4자리씩 끊는다: ABCD2345 → ABCD-2345 */
export function formatInviteCode(code: string): string {
  return code.length === 8 ? `${code.slice(0, 4)}-${code.slice(4)}` : code
}

export function inviteLink(code: string): string {
  return `${window.location.origin}/join/${code}`
}
