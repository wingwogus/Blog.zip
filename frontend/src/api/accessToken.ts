/**
 * accessToken은 메모리에만 둔다. localStorage에 저장하지 않는다.
 *
 * localStorage에 두면 XSS 하나로 탈취된다. 새로고침 시 사라지는 것은
 * refreshToken(HttpOnly 쿠키)으로 재발급해 복구한다.
 *
 * docs/decisions/002-auth-strategy.md
 */
let accessToken: string | null = null;

/** 토큰이 사라졌을 때(재발급 실패) 알림을 받을 구독자. 로그인 화면 전환에 쓴다. */
type ExpiredListener = () => void;
const expiredListeners = new Set<ExpiredListener>();

export function getAccessToken(): string | null {
  return accessToken;
}

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function clearAccessToken(): void {
  accessToken = null;
}

export function onSessionExpired(listener: ExpiredListener): () => void {
  expiredListeners.add(listener);
  return () => expiredListeners.delete(listener);
}

export function notifySessionExpired(): void {
  accessToken = null;
  for (const listener of expiredListeners) listener();
}
