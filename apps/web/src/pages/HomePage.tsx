import { LogOut, Plus, Ticket } from 'lucide-react'
import { Button, Logo } from '../components/ui'
import { useAuth } from '../providers/authContext'

/** 1단계: 로그인 후 첫 화면. 그룹 기능은 2단계에서 채운다 */
export function HomePage() {
  const { user, logout } = useAuth()

  return (
    <div className="mx-auto flex min-h-dvh w-full max-w-md flex-col">
      <header className="flex items-center justify-between px-5 py-4">
        <Logo className="text-stone-900" />
        <button
          onClick={logout}
          className="inline-flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm text-stone-500 hover:bg-stone-100"
        >
          <LogOut className="size-4" aria-hidden />
          로그아웃
        </button>
      </header>

      <main className="flex flex-1 flex-col px-5 pb-10">
        <section className="mt-4">
          <p className="text-stone-500">안녕하세요,</p>
          <h1 className="text-2xl font-bold">{user?.nickname}님 🙌</h1>
        </section>

        <section className="mt-8 flex flex-1 flex-col items-center justify-center rounded-3xl border border-dashed border-stone-300 bg-white px-6 py-14 text-center">
          <div className="flex size-14 items-center justify-center rounded-2xl bg-brand-50 text-3xl">🌱</div>
          <h2 className="mt-5 text-lg font-semibold">아직 참여한 크루가 없어요</h2>
          <p className="mt-2 text-sm leading-relaxed text-stone-500">
            크루를 직접 만들거나, 친구에게 받은
            <br />
            초대 코드로 참여해 보세요.
          </p>
          <div className="mt-8 flex w-full flex-col gap-2">
            <Button disabled title="2단계에서 열려요">
              <Plus className="size-4" aria-hidden />
              크루 만들기
            </Button>
            <Button variant="ghost" disabled title="2단계에서 열려요">
              <Ticket className="size-4" aria-hidden />
              초대 코드로 참여
            </Button>
          </div>
          <p className="mt-4 text-xs text-stone-400">크루 기능은 다음 단계에서 열려요</p>
        </section>
      </main>
    </div>
  )
}
