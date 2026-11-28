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
  // CI: 실패 내용을 GitHub 실행 화면에 주석(annotation)으로 띄운다 (로그는 로그인해야 보이지만 주석은 바로 보인다)
  reporter: process.env.CI ? [['github'], ['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL,
    locale: 'ko-KR',
    timezoneId: 'Asia/Seoul',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  // 휴대폰(한 줄 화면)과 데스크톱(사이드바·두 칸 화면) 양쪽에서 같은 흐름을 확인한다
  projects: [
    { name: 'mobile', use: { ...devices['Pixel 7'] } },
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } } },
  ],
})
