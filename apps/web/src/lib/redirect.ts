import { useLocation } from 'react-router'

/** 로그인 후 원래 가려던 페이지로 돌려보낸다 (같은 사이트 안의 경로만 허용해 외부 주소로 보내지 않는다) */
export function useRedirectTarget(): string {
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from
  return from && from.startsWith('/') && !from.startsWith('//') ? from : '/'
}
