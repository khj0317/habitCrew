import { expect, test } from '@playwright/test'
import { signup, uniqueSuffix } from './helpers'

test('회원가입 → 크루 만들기 → 초대 링크로 친구가 참여', async ({ browser }) => {
  const suffix = uniqueSuffix()

  // 크루장: 가입하고 크루를 만든다
  const ownerContext = await browser.newContext()
  const owner = await ownerContext.newPage()
  await signup(owner, `크루장${suffix}`)
  await owner.getByRole('button', { name: '크루 만들기' }).click()
  await owner.getByLabel('크루 이름').fill(`E2E 크루 ${suffix}`)
  await owner.getByRole('button', { name: '토' }).click()
  await owner.getByRole('button', { name: '500P' }).click()
  await owner.getByRole('button', { name: '크루 만들기' }).click()
  await expect(owner.getByText('🎉 크루를 만들었어요!')).toBeVisible()
  const code = (await owner.getByRole('button', { name: /초대 코드 .* 복사/ }).getAttribute('aria-label'))!.match(
    /초대 코드 (\w{8})/,
  )![1]

  // 친구: 로그인하지 않은 채 초대 링크를 열면, 가입 후 초대 화면으로 돌아온다
  const friendContext = await browser.newContext()
  const friend = await friendContext.newPage()
  await friend.goto(`/join/${code.toLowerCase()}`)
  await expect(friend).toHaveURL(/\/login/)
  await friend.getByRole('link', { name: '회원가입' }).click()
  await friend.getByLabel('이메일').fill(`e2e-friend${suffix}@example.com`)
  await friend.getByLabel('비밀번호').fill('Password1')
  await friend.getByLabel('닉네임').fill(`친구${suffix}`)
  await friend.getByRole('button', { name: '가입하고 시작하기' }).click()
  await expect(friend.getByRole('heading', { name: `E2E 크루 ${suffix}` })).toBeVisible()
  await friend.getByRole('button', { name: '크루 참여하기' }).click()
  await expect(friend.getByText('🎉 크루에 참여했어요!')).toBeVisible()
  await expect(friend.getByRole('heading', { name: /크루원 2/ })).toBeVisible()

  // 크루장 화면에도 2명이 보인다
  await owner.reload()
  await expect(owner.getByRole('heading', { name: /크루원 2/ })).toBeVisible()

  await ownerContext.close()
  await friendContext.close()
})
