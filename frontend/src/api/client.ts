import { ApiError, NetworkError } from './ApiError';
import { ErrorCode } from './errorCodes';
import {
  clearAccessToken,
  getAccessToken,
  notifySessionExpired,
  setAccessToken,
} from './accessToken';
import type { ApiEnvelope, RefreshResult } from './types';

/**
 * API 클라이언트.
 *
 * 여기서만 처리하고 컴포넌트로 흘리지 않는 것:
 *  1. {success, data, error} 래퍼 해제
 *  2. 실패를 ApiError(code 포함)로 변환
 *  3. 401 자동 재발급 + 원요청 1회 재시도
 *  4. 재발급 요청 단일화(single-flight)
 *
 * docs/decisions/002-auth-strategy.md, docs/decisions/010-api-response-contract.md
 */

const BASE_PATH = '/api/v1';
const REFRESH_PATH = `${BASE_PATH}/auth/token/refresh`;

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE';
  body?: unknown;
  query?: Record<string, string | number | boolean | undefined>;
  /** 내부 재시도 제어용. 호출자가 쓰지 않는다. */
  retryOnUnauthorized?: boolean;
}

/**
 * 진행 중인 재발급 요청. 동시에 여러 요청이 401을 받아도 재발급은 한 번만 호출한다.
 *
 * 이게 없으면 각 요청이 따로 재발급을 호출하고, 서버가 refreshToken을 회전시키므로
 * 나중 응답이 앞선 토큰을 무효화해 전부 로그아웃된다.
 * docs/decisions/002-auth-strategy.md
 */
let refreshInFlight: Promise<string> | null = null;

function buildUrl(path: string, query?: RequestOptions['query']): string {
  const url = `${BASE_PATH}${path}`;
  if (!query) return url;
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined) params.set(key, String(value));
  }
  const qs = params.toString();
  return qs ? `${url}?${qs}` : url;
}

async function parseEnvelope<T>(response: Response): Promise<ApiEnvelope<T> | null> {
  if (response.status === 204) return null;
  try {
    return (await response.json()) as ApiEnvelope<T>;
  } catch (cause) {
    throw new NetworkError('응답을 해석할 수 없습니다.', { cause });
  }
}

async function rawRequest(path: string, options: RequestOptions): Promise<Response> {
  const headers: Record<string, string> = {};
  const token = getAccessToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';

  try {
    return await fetch(buildUrl(path, options.query), {
      method: options.method ?? 'GET',
      headers,
      // refreshToken이 HttpOnly 쿠키이므로 항상 자격 증명을 포함한다.
      credentials: 'include',
      ...(options.body !== undefined ? { body: JSON.stringify(options.body) } : {}),
    });
  } catch (cause) {
    throw new NetworkError('네트워크에 연결할 수 없습니다.', { cause });
  }
}

/** 재발급을 호출한다. 이미 진행 중이면 그 결과를 기다린다. */
async function refreshAccessToken(): Promise<string> {
  if (refreshInFlight) return refreshInFlight;

  refreshInFlight = (async () => {
    const response = await rawRequest('/auth/token/refresh', { method: 'POST' });
    const envelope = await parseEnvelope<RefreshResult>(response);

    if (!response.ok || !envelope?.data) {
      // 재발급이 실패하면 세션이 끝난 것이다. 재시도하지 않는다.
      notifySessionExpired();
      throw new ApiError(
        response.status,
        envelope?.error ?? {
          code: ErrorCode.INVALID_REFRESH_TOKEN,
          messageKey: 'error.invalid_refresh_token',
          message: '로그인이 만료되었습니다. 다시 로그인해 주세요.',
        },
      );
    }

    setAccessToken(envelope.data.accessToken);
    return envelope.data.accessToken;
  })().finally(() => {
    refreshInFlight = null;
  });

  return refreshInFlight;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const retryOnUnauthorized = options.retryOnUnauthorized ?? true;
  const response = await rawRequest(path, options);

  // 재발급 엔드포인트 자체의 401에는 재시도하지 않는다. 무한 루프가 된다.
  const isRefreshCall = buildUrl(path) === REFRESH_PATH;
  const envelope = response.status === 401 ? await parseEnvelope<T>(response) : null;

  if (
    response.status === 401 &&
    envelope?.error?.code === ErrorCode.UNAUTHORIZED &&
    retryOnUnauthorized &&
    !isRefreshCall
  ) {
    await refreshAccessToken();
    // 재시도는 1회만. 다시 401이면 아래에서 ApiError로 던진다.
    return request<T>(path, { ...options, retryOnUnauthorized: false });
  }

  const parsedEnvelope = envelope ?? (await parseEnvelope<T>(response));

  if (!response.ok) {
    if (response.status === 401 && !isRefreshCall) clearAccessToken();
    throw new ApiError(
      response.status,
      parsedEnvelope?.error ?? {
        code: ErrorCode.INTERNAL_ERROR,
        messageKey: 'error.internal_error',
        message: '문제가 발생했습니다. 잠시 후 다시 시도해 주세요.',
      },
    );
  }

  // 204는 본문이 없다.
  return (parsedEnvelope?.data ?? undefined) as T;
}

export const api = {
  get: <T>(path: string, query?: RequestOptions['query']) =>
    request<T>(path, { method: 'GET', ...(query ? { query } : {}) }),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', ...(body !== undefined ? { body } : {}) }),
  patch: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'PATCH', ...(body !== undefined ? { body } : {}) }),
  delete: <T = void>(path: string) => request<T>(path, { method: 'DELETE' }),
};

/** 테스트에서 single-flight 상태를 초기화한다. */
export function resetRefreshState(): void {
  refreshInFlight = null;
}
