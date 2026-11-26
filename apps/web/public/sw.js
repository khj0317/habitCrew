// HabitCrew 서비스 워커: 웹 푸시 알림을 받아 보여 주고, 누르면 해당 화면을 연다.
// 앱이 닫혀 있어도 브라우저가 이 파일만 깨워서 실행한다.

self.addEventListener('install', () => self.skipWaiting())
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()))

self.addEventListener('push', (event) => {
  let data = {}
  try {
    data = event.data ? event.data.json() : {}
  } catch {
    data = { title: 'HabitCrew', body: event.data ? event.data.text() : '' }
  }
  event.waitUntil(
    self.registration.showNotification(data.title || 'HabitCrew', {
      body: data.body || '',
      icon: '/icons/icon-192.png',
      badge: '/icons/icon-192.png',
      tag: data.tag, // 같은 tag면 알림창에서 이전 알림을 바꿔 끼운다 (쌓이지 않게)
      renotify: Boolean(data.tag),
      data: { url: data.url || '/' },
    }),
  )
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  // 같은 사이트 안의 경로만 연다
  const path = typeof event.notification.data?.url === 'string' && event.notification.data.url.startsWith('/')
    ? event.notification.data.url
    : '/'
  const target = new URL(path, self.location.origin).href
  event.waitUntil(
    (async () => {
      const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
      // 이미 열린 앱 창이 있으면 그 창을 앞으로 가져와 이동하고, 없으면 새로 연다
      const existing = windows.find((w) => new URL(w.url).origin === self.location.origin)
      if (existing) {
        await existing.focus()
        return existing.navigate(target)
      }
      return self.clients.openWindow(target)
    })(),
  )
})
