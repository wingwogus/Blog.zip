import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/api/ApiError';
import { ErrorCode } from '@/api/errorCodes';
import { getAccessToken } from '@/api/accessToken';
import { login, restoreSession, signup } from './authApi';

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const userResult = {
  user: {
    id: 'usr_1',
    email: 'user@example.com',
    nickname: '재현',
    createdAt: '2026-08-30T09:12:00Z',
  },
  accessToken: 'access-token',
};

describe('authApi', () => {
  it('로그인 성공 응답의 accessToken을 반환한다', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(200, { success: true, data: userResult, error: null }));
    vi.stubGlobal('fetch', fetchMock);

    const result = await login({
      email: 'user@example.com',
      password: 'password1234',
    });

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/auth/login');
    expect(result.accessToken).toBe('access-token');
    expect(getAccessToken()).toBe('access-token');
  });

  it('가입 성공 응답의 accessToken을 반환한다', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(201, { success: true, data: userResult, error: null }));
    vi.stubGlobal('fetch', fetchMock);

    const result = await signup({
      email: 'user@example.com',
      password: 'password1234',
      nickname: '재현',
    });

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/auth/signup');
    expect(result.accessToken).toBe('access-token');
    expect(getAccessToken()).toBe('access-token');
  });

  it('가입 실패를 code가 있는 ApiError로 받는다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(409, {
          success: false,
          data: null,
          error: {
            code: ErrorCode.DUPLICATE_EMAIL,
            messageKey: 'error.x',
            message: '문구는 바뀔 수 있다',
          },
        }),
      ),
    );

    const error = await signup({
      email: 'user@example.com',
      password: 'password1234',
      nickname: '재현',
    }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).code).toBe(ErrorCode.DUPLICATE_EMAIL);
  });

  it('세션 복구가 성공하면 accessToken을 메모리에 둔다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(200, {
          success: true,
          data: { accessToken: 'fresh-token' },
          error: null,
        }),
      ),
    );

    await expect(restoreSession()).resolves.toBe(true);
    expect(getAccessToken()).toBe('fresh-token');
  });

  it('세션 복구가 실패하면 accessToken을 두지 않는다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(401, {
          success: false,
          data: null,
          error: {
            code: ErrorCode.INVALID_REFRESH_TOKEN,
            messageKey: 'error.x',
            message: '문구는 바뀔 수 있다',
          },
        }),
      ),
    );

    await expect(restoreSession()).resolves.toBe(false);
    expect(getAccessToken()).toBeNull();
  });
});
