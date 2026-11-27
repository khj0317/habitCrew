import { expect, type Page } from '@playwright/test'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))

/** README용 스크린샷: SCREENSHOTS=1 일 때만 docs/screenshots에 저장한다 */
export async function capture(page: Page, name: string) {
  if (!process.env.SCREENSHOTS) return
  // 개발 서버는 HMR 연결이 계속 열려 있어 networkidle을 기다리면 끝나지 않는다: 사진이 다 뜰 때까지만 기다린다
  await page.waitForFunction(() => [...document.images].every((img) => img.complete))
  await page.waitForTimeout(300)
  await page.screenshot({ path: path.join(here, '..', '..', '..', 'docs', 'screenshots', `${name}.png`) })
}

/** 인증 사진으로 쓸 이미지 (앱 아이콘 PNG를 재사용) */
export function samplePhoto() {
  return {
    name: 'photo.png',
    mimeType: 'image/png',
    buffer: readFileSync(path.join(here, '..', 'public', 'icons', 'icon-512.png')),
  }
}

export function uniqueSuffix() {
  return Math.random().toString(36).slice(2, 8)
}

export async function signup(page: Page, nickname: string) {
  await page.goto('/signup')
  await page.getByLabel('이메일').fill(`e2e-${nickname}@example.com`)
  await page.getByLabel('비밀번호').fill('Password1')
  await page.getByLabel('닉네임').fill(nickname)
  await page.getByRole('button', { name: '가입하고 시작하기' }).click()
  await expect(page.getByRole('heading', { name: new RegExp(`${nickname}님`) })).toBeVisible()
}
