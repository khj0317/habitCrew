import { Sparkles } from 'lucide-react'
import { useNavigate } from 'react-router'
import { useAuth } from '../providers/authContext'

/** 체험 계정일 때 홈 위쪽에 남은 시간과 회원가입 안내를 보여 준다 */
export function GuestBanner() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  if (!user?.guest || !user.guestExpiresAt) return null

  const hoursLeft = Math.max(0, Math.ceil((new Date(user.guestExpiresAt).getTime() - Date.now()) / 3_600_000))

  return (
    <section className="rounded-2xl border-2 border-ink bg-brand-600 px-4 py-4 text-white shadow-hard" role="note">
      <p className="flex items-center gap-1.5 text-sm font-semibold">
        <Sparkles className="size-4" aria-hidden />
        체험 계정으로 둘러보는 중이에요
      </p>
      <p className="mt-1 text-[13px] leading-relaxed text-white/85">
        크루원 3명과 2주치 기록을 미리 채워 두었어요. 오늘 인증을 직접 올려 보세요! 이 계정은 약 {hoursLeft}시간 뒤 사라져요.
      </p>
      <button
        onClick={async () => {
          await logout()
          navigate('/signup')
        }}
        className="mt-3 rounded-lg border-2 border-ink bg-lime-400 px-3 py-1.5 text-xs font-bold text-ink shadow-hard-sm hover:bg-lime-300"
      >
        회원가입하고 내 크루 만들기
      </button>
    </section>
  )
}
