import { useCallback, useEffect, useState } from 'react'
import { api } from './api'

/**
 * 이 기기(브라우저)의 푸시 알림 상태
 * - unsupported: 브라우저가 지원하지 않음
 * - ios-install: 아이폰·아이패드는 "홈 화면에 추가"한 앱에서만 알림을 받을 수 있음
 * - denied: 사용자가 브라우저에서 알림을 차단함 (브라우저 설정에서만 풀 수 있음)
 * - off / on: 꺼짐 / 켜짐
 */
export type PushState = 'loading' | 'unsupported' | 'ios-install' | 'denied' | 'off' | 'on'

function isIos() {
  return /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)
}

function isStandalone() {
  return window.matchMedia('(display-mode: standalone)').matches || (navigator as { standalone?: boolean }).standalone === true
}

function supported() {
  return 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window
}

/** VAPID 공개 키(base64url)를 PushManager가 받는 바이트 배열로 바꾼다 */
function toBytes(base64Url: string): Uint8Array<ArrayBuffer> {
  const base64 = (base64Url + '='.repeat((4 - (base64Url.length % 4)) % 4)).replace(/-/g, '+').replace(/_/g, '/')
  const raw = atob(base64)
  const bytes = new Uint8Array(new ArrayBuffer(raw.length))
  for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i)
  return bytes
}

async function currentSubscription() {
  const registration = await navigator.serviceWorker.ready
  return registration.pushManager.getSubscription()
}

export function usePush() {
  const [state, setState] = useState<PushState>('loading')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    if (!supported()) {
      setState(isIos() && !isStandalone() ? 'ios-install' : 'unsupported')
      return
    }
    if (Notification.permission === 'denied') {
      setState('denied')
      return
    }
    try {
      const subscription = await currentSubscription()
      setState(subscription ? 'on' : 'off')
    } catch {
      setState('unsupported')
    }
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  const enable = useCallback(async () => {
    setBusy(true)
    setError(null)
    try {
      // 권한 창은 사용자가 버튼을 누른 직후에만 띄운다 (페이지를 열자마자 묻지 않는다)
      const permission = await Notification.requestPermission()
      if (permission !== 'granted') {
        setState(permission === 'denied' ? 'denied' : 'off')
        return
      }
      const { publicKey } = await api<{ publicKey: string }>('/api/push/public-key')
      const registration = await navigator.serviceWorker.ready
      let subscription = await registration.pushManager.getSubscription()
      if (!subscription) {
        subscription = await registration.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: toBytes(publicKey),
        })
      }
      await api('/api/push/subscriptions', { method: 'POST', body: subscription.toJSON() })
      setState('on')
    } catch (err) {
      setError(err instanceof Error ? err.message : '알림을 켜지 못했어요.')
    } finally {
      setBusy(false)
    }
  }, [])

  const disable = useCallback(async () => {
    setBusy(true)
    setError(null)
    try {
      const subscription = await currentSubscription()
      if (subscription) {
        await api('/api/push/subscriptions', { method: 'DELETE', body: { endpoint: subscription.endpoint } })
        await subscription.unsubscribe()
      }
      setState('off')
    } catch (err) {
      setError(err instanceof Error ? err.message : '알림을 끄지 못했어요.')
    } finally {
      setBusy(false)
    }
  }, [])

  const sendTest = useCallback(async () => {
    setError(null)
    try {
      await api('/api/push/test', { method: 'POST' })
    } catch (err) {
      setError(err instanceof Error ? err.message : '시험 알림을 보내지 못했어요.')
    }
  }, [])

  return { state, busy, error, enable, disable, sendTest }
}
