import { expect, test } from '@playwright/test'
import { capture, samplePhoto } from './helpers'

test('로그인 없이 둘러보기 → 사진 인증 → 좋아요·댓글 → 기록 확인', async ({ page }) => {
  // 1) 체험 계정으로 시작
  await page.goto('/login')
  await capture(page, '01-login')
  await page.getByRole('button', { name: /로그인 없이 둘러보기/ }).click()
  await expect(page.getByText('체험 계정으로 둘러보는 중이에요')).toBeVisible()
  await capture(page, '02-home')

  // 2) 체험 크루: 2주치 기록이 채워져 있다
  await page.getByRole('link', { name: /하루 한 페이지 독서 \(체험\)/ }).click()
  await expect(page.getByText('오늘 인증이 남았어요')).toBeVisible()
  await expect(page.getByText('2,000P')).toBeVisible()
  await capture(page, '03-crew')

  // 3) 사진으로 인증하기
  await page.getByRole('button', { name: '사진으로 인증하기' }).click()
  await page.locator('#photo-input').setInputFiles(samplePhoto())
  await expect(page.getByAltText('올릴 인증 사진 미리보기')).toBeVisible()
  await page.getByLabel(/한마디/).fill('E2E 테스트로 올린 인증!')
  await capture(page, '04-certify')
  await page.getByRole('button', { name: '인증 올리기' }).click()
  await expect(page.getByText('오늘 인증 완료! 🎉')).toBeVisible()
  await expect(page.getByText('E2E 테스트로 올린 인증!')).toBeVisible()

  // 4) 다른 크루원 인증에 좋아요 (누르는 즉시 반영)
  const firstOtherLike = page.getByRole('button', { name: '좋아요', exact: true }).first()
  await firstOtherLike.click()
  await expect(page.getByRole('button', { name: '좋아요 취소' }).first()).toBeVisible()
  await capture(page, '05-feed')

  // 5) 내 인증을 열어 댓글 달기
  await page.getByRole('link', { name: /인증 사진 크게 보기/ }).first().click()
  await page.getByLabel('댓글', { exact: true }).fill('내일도 화이팅!')
  await page.getByRole('button', { name: '댓글 보내기' }).click()
  await expect(page.getByText('내일도 화이팅!')).toBeVisible()
  await capture(page, '06-detail')

  // 6) 기록: 랭킹·달력·정산 내역
  await page.goBack()
  await page.getByRole('link', { name: /랭킹 · 달력 · 정산 내역 보기/ }).click()
  await expect(page.getByRole('heading', { name: '월간 랭킹' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '인증 달력' })).toBeVisible()
  await expect(page.getByText('🔥 5일 연속').first()).toBeVisible() // 4일 연속 + 오늘 인증
  await capture(page, '07-records')
})
