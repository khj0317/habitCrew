import { expect, test } from '@playwright/test'
import { signup, uniqueSuffix } from './helpers'

/** 개발용 메일 서버(Mailpit)의 API. 앱이 보낸 비밀번호 재설정 메일을 꺼내 본다 */
const MAILPIT = process.env.MAILPIT_URL ?? 'http://localhost:8026'

test('닉네임 바꾸기 → 비밀번호 찾기 메일 → 새 비밀번호로 로그인', async ({ page, request }) => {
  const suffix = uniqueSuffix()
  const nickname = `계정${suffix}`
  const email = `e2e-account-${suffix}@example.com`
  await signup(page, nickname, email)

  // 설정에서 닉네임 바꾸기
  await page.goto('/settings')
  await page.getByLabel('닉네임').fill(`바뀐${suffix}`)
  await page.getByRole('button', { name: '닉네임 저장' }).click()
  await expect(page.getByText('바꿨어요 ✓')).toBeVisible()

  // 로그아웃하고 비밀번호 찾기
  await page.evaluate(() => fetch('/api/auth/logout', { method: 'POST' }))
  await page.goto('/login')
  await page.getByRole('link', { name: '비밀번호를 잊으셨나요?' }).click()
  // 화면이 바뀐 뒤에 입력한다 (느린 CI에서는 바뀌기 전 로그인 화면의 이메일 칸에 입력되는 일이 있었다)
  await expect(page).toHaveURL(/\/forgot-password$/)
  await expect(page.getByRole('heading', { name: /비밀번호 찾기/ })).toBeVisible()
  await page.getByLabel('이메일').fill(email)
  await expect(page.getByRole('button', { name: '재설정 링크 보내기' })).toBeEnabled()
  await page.getByRole('button', { name: '재설정 링크 보내기' }).click()
  await expect(page.getByText('메일을 확인해 주세요')).toBeVisible()

  // 메일함(Mailpit)에서 링크 꺼내기 (메일은 따로 보내므로 잠깐 기다린다)
  let link = ''
  await expect(async () => {
    const search = await request.get(`${MAILPIT}/api/v1/search`, { params: { query: `to:${email}` } })
    const { messages } = await search.json()
    expect(messages.length).toBeGreaterThan(0)
    const message = await (await request.get(`${MAILPIT}/api/v1/message/${messages[0].ID}`)).json()
    link = message.Text.match(/https?:\/\/\S+reset-password\?token=[\w-]+/)[0]
  }).toPass({ timeout: 10_000 })

  // 링크로 새 비밀번호 정하기 (메일 속 주소의 경로만 써서 테스트 서버로 연다)
  await page.goto(new URL(link).pathname + new URL(link).search)
  await page.getByLabel('새 비밀번호', { exact: true }).fill('NewPassword2')
  await page.getByLabel('새 비밀번호 확인').fill('NewPassword2')
  await page.getByRole('button', { name: '비밀번호 바꾸기' }).click()
  await expect(page.getByText('비밀번호를 바꿨어요. 새 비밀번호로 로그인해 주세요.')).toBeVisible()

  await page.getByLabel('이메일').fill(email)
  await page.getByLabel('비밀번호').fill('NewPassword2')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByRole('heading', { name: new RegExp(`바뀐${suffix}님`) })).toBeVisible()
})
