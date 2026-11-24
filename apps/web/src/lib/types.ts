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

export type Author = { userId: number; nickname: string }

export type Certification = {
  id: number
  author: Author
  certDate: string
  message: string | null
  imageUrl: string
  thumbnailUrl: string
  imageWidth: number
  imageHeight: number
  likeCount: number
  likedByMe: boolean
  commentCount: number
  mine: boolean
  createdAt: string
}

export type Feed = { items: Certification[]; nextCursor: number | null }

export type TodayStatus = {
  date: string
  certDay: boolean
  deadline: string
  myCertificationId: number | null
  certifiedCount: number
  memberCount: number
  members: { userId: number; nickname: string; certified: boolean; certificationId: number | null }[]
}

export type Comment = { id: number; author: Author; content: string; mine: boolean; createdAt: string }

export type MemberStats = {
  userId: number
  nickname: string
  currentStreak: number
  totalCertifications: number
  missedCount: number
  totalPenaltyPoints: number
}

export type CrewStats = { me: MemberStats; members: MemberStats[] }

export type Settlement = {
  date: string
  targetCount: number
  certifiedCount: number
  penalties: { userId: number; nickname: string; points: number }[]
}

export type SettlementHistory = { items: Settlement[]; nextBefore: string | null }

export type TokenResponse = {
  accessToken: string
  expiresIn: number
  user: User
}
