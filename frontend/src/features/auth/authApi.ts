import { api } from '@/api/client';
import { setAccessToken } from '@/api/accessToken';
import type { LoginResult, RefreshResult } from '@/api/types';

/**
 * 인증 API. fetch는 api 클라이언트만 호출한다.
 * 자동 재발급과 single-flight는 클라이언트가 담당한다.
 * docs/specs/auth.md, docs/decisions/002-auth-strategy.md
 */

export interface SignupInput {
  email: string;
  password: string;
  nickname: string;
}

export interface LoginInput {
  email: string;
  password: string;
}

export async function signup(input: SignupInput): Promise<LoginResult> {
  const result = await api.post<LoginResult>('/auth/signup', input);
  setAccessToken(result.accessToken);
  return result;
}

export async function login(input: LoginInput): Promise<LoginResult> {
  const result = await api.post<LoginResult>('/auth/login', input);
  setAccessToken(result.accessToken);
  return result;
}

/**
 * 새로고침 후 accessToken 복구. StrictMode 이중 마운트에서도 재발급은 한 번만 나간다.
 * refreshToken 회전이 있으므로 두 번 부르면 서로를 무효화한다.
 */
let restoreInFlight: Promise<boolean> | null = null;

export function restoreSession(): Promise<boolean> {
  if (restoreInFlight) return restoreInFlight;

  restoreInFlight = api
    .post<RefreshResult>('/auth/token/refresh')
    .then((result) => {
      setAccessToken(result.accessToken);
      return true;
    })
    .catch(() => false);

  return restoreInFlight;
}

export function markSessionEstablished(): void {
  restoreInFlight = Promise.resolve(true);
}

export function resetSessionRestore(): void {
  restoreInFlight = null;
}
