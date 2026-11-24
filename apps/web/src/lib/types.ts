export type User = {
  id: number
  email: string
  nickname: string
  createdAt: string
}

export type DayOfWeek = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'

export type CrewRole = 'OWNER' | 'MEMBER'

export type CrewSummary = {
  id: number
  name: string
  description: string | null
  certDays: DayOfWeek[]
  penaltyPoints: number
  memberCount: number
  maxMembers: number
  myRole: CrewRole
}

export type CrewMember = {
  userId: number
  nickname: string
  role: CrewRole
  joinedAt: string
}

export type CrewDetail = {
  id: number
  name: string
  description: string | null
  certDays: DayOfWeek[]
  penaltyPoints: number
  maxMembers: number
  inviteCode: string
  myRole: CrewRole
  createdAt: string
  members: CrewMember[]
}

export type InvitePreview = {
  crewId: number
  name: string
  description: string | null
  certDays: DayOfWeek[]
  penaltyPoints: number
  memberCount: number
  maxMembers: number
  ownerNickname: string
  alreadyJoined: boolean
}

export type TokenResponse = {
  accessToken: string
  expiresIn: number
  user: User
}
