import { defineConfig, devices } from '@playwright/test'

/**
 * 브라우저로 핵심 흐름을 끝까지 확인하는 E2E 테스트.
 * 백엔드와 웹이 떠 있어야 한다. 로컬: dev.cmd(또는 npm run dev)로 켠 뒤 npm run test:e2e -w @habitcrew/web
 * 이미 떠 있는 다른 주소를 쓰려면 E2E_BASE_URL=http://localhost:5180 처럼 지정한다.
 */
const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:5180'

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL,
    locale: 'ko-KR',
    timezoneId: 'Asia/Seoul',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  // 휴대폰 화면 기준으로 만든 앱이라 모바일 크기로 돌린다
  projects: [{ name: 'mobile-chrome', use: { ...devices['Pixel 7'] } }],
})
