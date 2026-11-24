export type User = {
  id: number
  email: string
  nickname: string
  createdAt: string
}

export type TokenResponse = {
  accessToken: string
  expiresIn: number
  user: User
}
