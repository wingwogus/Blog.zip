import { describe, expect, it, vi } from 'vitest';
import { api } from './client';
import { ApiError } from './ApiError';
import { ErrorCode } from './errorCodes';
import { getAccessToken, setAccessToken } from './accessToken';

/**
 * docs/decisions/002-auth-strategy.md 가 요구하는 동작을 고정한다.
 *
 * 문구(message)는 검증하지 않는다. 서버가 바꿀 수 있는 값이다.
 * code와 동작만 본다. docs/decisions/010-api-response-contract.md
 */

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const ok = <T>(data: T) => jsonResponse(200, { success: true, data, error: null });

const fail = (status: number, code: string) =>
  jsonResponse(status, {
    success: false,
    data: null,
    error: { code, messageKey: 'error.x', message: '문구는 바뀔 수 있다' },
  });

describe('api client', () => {
  it('응답 래퍼를 벗겨 data를 반환한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ok({ items: [{ postId: 'p1' }], nextCursor: null })),
    );

    const result = await api.get<{ items: unknown[]; nextCursor: string | null }>('/feed');

    expect(result.items).toHaveLength(1);
    expect(result.nextCursor).toBeNull();
  });

  it('accessToken이 있으면 Authorization 헤더를 붙인다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok({}));
    vi.stubGlobal('fetch', fetchMock);
    setAccessToken('token-abc');

    await api.get('/users/me');

    const init = fetchMock.mock.calls[0]?.[1] as RequestInit;
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer token-abc');
  });

  it('refreshToken 쿠키를 위해 항상 credentials를 포함한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok({}));
    vi.stubGlobal('fetch', fetchMock);

    await api.get('/feed');

    const init = fetchMock.mock.calls[0]?.[1] as RequestInit;
    expect(init.credentials).toBe('include');
  });

  it('실패 응답을 code가 있는 ApiError로 변환한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(fail(409, ErrorCode.ALREADY_SUBSCRIBED)),
    );

    const error = await api.post('/subscriptions', {}).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).code).toBe(ErrorCode.ALREADY_SUBSCRIBED);
    expect((error as ApiError).status).toBe(409);
  });

  it('204 응답을 오류 없이 처리한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })));

    await expect(api.delete('/subscriptions/sub_1')).resolves.toBeUndefined();
  });

  it('query 파라미터의 undefined는 보내지 않는다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok({}));
    vi.stubGlobal('fetch', fetchMock);

    await api.get('/feed', { cursor: undefined, size: 20 });

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/feed?size=20');
  });

  describe('자동 재발급', () => {
    it('401이면 재발급 후 원래 요청을 재시도한다', async () => {
      const fetchMock = vi
        .fn()
        // 1) 원요청 401
        .mockResolvedValueOnce(fail(401, ErrorCode.UNAUTHORIZED))
        // 2) 재발급 성공
        .mockResolvedValueOnce(ok({ accessToken: 'fresh-token' }))
        // 3) 재시도 성공
        .mockResolvedValueOnce(ok({ nickname: '재현' }));
      vi.stubGlobal('fetch', fetchMock);

      const result = await api.get<{ nickname: string }>('/users/me');

      expect(result.nickname).toBe('재현');
      expect(fetchMock).toHaveBeenCalledTimes(3);
      expect(getAccessToken()).toBe('fresh-token');
    });

    it('재발급 후에도 401이면 재시도하지 않고 던진다', async () => {
      const fetchMock = vi
        .fn()
        .mockResolvedValueOnce(fail(401, ErrorCode.UNAUTHORIZED))
        .mockResolvedValueOnce(ok({ accessToken: 'fresh-token' }))
        .mockResolvedValueOnce(fail(401, ErrorCode.UNAUTHORIZED));
      vi.stubGlobal('fetch', fetchMock);

      const error = await api.get('/users/me').catch((e: unknown) => e);

      expect(error).toBeInstanceOf(ApiError);
      // 원요청 + 재발급 + 재시도 = 3. 무한 루프가 되지 않는다.
      expect(fetchMock).toHaveBeenCalledTimes(3);
    });

    it('동시에 401을 받아도 재발급은 한 번만 호출한다', async () => {
      // 이게 깨지면 회전된 refreshToken이 서로를 무효화해 전부 로그아웃된다.
      const fetchMock = vi.fn().mockImplementation((url: string) => {
        if (url === '/api/v1/auth/token/refresh') {
          return Promise.resolve(ok({ accessToken: 'fresh-token' }));
        }
        return Promise.resolve(
          getAccessToken() === 'fresh-token'
            ? ok({ value: 'after-refresh' })
            : fail(401, ErrorCode.UNAUTHORIZED),
        );
      });
      vi.stubGlobal('fetch', fetchMock);

      const results = await Promise.all([
        api.get<{ value: string }>('/feed'),
        api.get<{ value: string }>('/subscriptions'),
        api.get<{ value: string }>('/users/me'),
      ]);

      expect(results.map((r) => r.value)).toEqual([
        'after-refresh',
        'after-refresh',
        'after-refresh',
      ]);

      const refreshCalls = fetchMock.mock.calls.filter(
        ([url]) => url === '/api/v1/auth/token/refresh',
      );
      expect(refreshCalls).toHaveLength(1);
    });

    it('재발급 엔드포인트의 401에는 재시도하지 않는다', async () => {
      const fetchMock = vi
        .fn()
        .mockResolvedValue(fail(401, ErrorCode.INVALID_REFRESH_TOKEN));
      vi.stubGlobal('fetch', fetchMock);

      const error = await api.post('/auth/token/refresh').catch((e: unknown) => e);

      expect(error).toBeInstanceOf(ApiError);
      expect((error as ApiError).code).toBe(ErrorCode.INVALID_REFRESH_TOKEN);
      expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    it('재발급이 실패하면 accessToken을 비운다', async () => {
      setAccessToken('stale-token');
      const fetchMock = vi
        .fn()
        .mockResolvedValueOnce(fail(401, ErrorCode.UNAUTHORIZED))
        .mockResolvedValueOnce(fail(401, ErrorCode.INVALID_REFRESH_TOKEN));
      vi.stubGlobal('fetch', fetchMock);

      await api.get('/feed').catch(() => undefined);

      expect(getAccessToken()).toBeNull();
    });
  });
});
