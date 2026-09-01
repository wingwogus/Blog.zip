import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ApiError } from '@/api/ApiError';
import { ErrorCode } from '@/api/errorCodes';
import { getAccessToken, notifySessionExpired, setAccessToken } from '@/api/accessToken';
import { GuestOnly, RequireAuth } from './AuthGates';
import { AuthSession } from './AuthSession';
import { LoginPage } from './LoginPage';
import { SignupPage } from './SignupPage';
import { login, restoreSession, signup } from './authApi';

vi.mock('./authApi', async () => {
  const actual = await vi.importActual<typeof import('./authApi')>('./authApi');
  return {
    ...actual,
    login: vi.fn(),
    signup: vi.fn(),
    restoreSession: vi.fn().mockResolvedValue(false),
  };
});

const loginMock = vi.mocked(login);
const signupMock = vi.mocked(signup);
const restoreSessionMock = vi.mocked(restoreSession);

beforeEach(() => {
  loginMock.mockReset();
  signupMock.mockReset();
  restoreSessionMock.mockReset();
  restoreSessionMock.mockResolvedValue(false);
});

const userResult = {
  user: {
    id: 'usr_1',
    email: 'user@example.com',
    nickname: '재현',
    createdAt: '2026-08-30T09:12:00Z',
  },
  accessToken: 'access-token',
};

function apiFail(code: string, status: number) {
  return new ApiError(status, {
    code,
    messageKey: 'error.x',
    message: '문구는 바뀔 수 있다',
  });
}

function renderAuth(path: string, options: { authenticated?: boolean } = {}) {
  if (options.authenticated) setAccessToken('existing-token');

  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <AuthSession>
          <Routes>
            <Route element={<GuestOnly />}>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/signup" element={<SignupPage />} />
            </Route>
            <Route element={<RequireAuth />}>
              <Route
                path="/"
                element={
                  <div>
                    <h1>피드</h1>
                    <a href="/subscriptions/new">친구 블로그 추가</a>
                  </div>
                }
              />
            </Route>
          </Routes>
        </AuthSession>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('로그인', () => {
  it('성공하면 accessToken을 메모리에 두고 홈으로 간다', async () => {
    loginMock.mockResolvedValue(userResult);
    renderAuth('/login');

    await userEvent.type(await screen.findByLabelText('이메일'), 'user@example.com');
    await userEvent.type(screen.getByLabelText('비밀번호'), 'password1234');
    await userEvent.click(screen.getByRole('button', { name: '로그인' }));

    expect(await screen.findByText('피드')).toBeInTheDocument();
    expect(getAccessToken()).toBe('access-token');
    expect(loginMock).toHaveBeenCalledWith({
      email: 'user@example.com',
      password: 'password1234',
    });
  });

  it('AUTH_004이면 로그인 화면에 머무르고 code로 오류를 표시한다', async () => {
    loginMock.mockRejectedValue(apiFail(ErrorCode.INVALID_CREDENTIALS, 401));
    renderAuth('/login');

    await userEvent.type(await screen.findByLabelText('이메일'), 'user@example.com');
    await userEvent.type(screen.getByLabelText('비밀번호'), 'wrong-password');
    await userEvent.click(screen.getByRole('button', { name: '로그인' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveAttribute('data-error-code', ErrorCode.INVALID_CREDENTIALS);
    expect(screen.getByRole('button', { name: '로그인' })).toBeInTheDocument();
    expect(getAccessToken()).toBeNull();
  });

  it('AUTH_006이면 code로 오류를 표시한다', async () => {
    loginMock.mockRejectedValue(apiFail(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS, 429));
    renderAuth('/login');

    await userEvent.type(await screen.findByLabelText('이메일'), 'user@example.com');
    await userEvent.type(screen.getByLabelText('비밀번호'), 'password1234');
    await userEvent.click(screen.getByRole('button', { name: '로그인' }));

    expect(await screen.findByRole('alert')).toHaveAttribute(
      'data-error-code',
      ErrorCode.TOO_MANY_LOGIN_ATTEMPTS,
    );
  });
});

describe('가입', () => {
  it('성공하면 accessToken을 메모리에 두고 추가 로그인 없이 홈으로 간다', async () => {
    signupMock.mockResolvedValue(userResult);
    renderAuth('/signup');

    await userEvent.type(await screen.findByLabelText('이름'), '재현');
    await userEvent.type(screen.getByLabelText('이메일'), 'user@example.com');
    await userEvent.type(screen.getByLabelText('비밀번호'), 'password1234');
    await userEvent.click(screen.getByRole('button', { name: '가입하고 시작' }));

    expect(await screen.findByText('피드')).toBeInTheDocument();
    expect(getAccessToken()).toBe('access-token');
    expect(signupMock).toHaveBeenCalledWith({
      email: 'user@example.com',
      password: 'password1234',
      nickname: '재현',
    });
  });

  it('AUTH_003이면 가입 화면에 머무르고 code로 오류를 표시한다', async () => {
    signupMock.mockRejectedValue(apiFail(ErrorCode.DUPLICATE_EMAIL, 409));
    renderAuth('/signup');

    await userEvent.type(await screen.findByLabelText('이름'), '재현');
    await userEvent.type(screen.getByLabelText('이메일'), 'user@example.com');
    await userEvent.type(screen.getByLabelText('비밀번호'), 'password1234');
    await userEvent.click(screen.getByRole('button', { name: '가입하고 시작' }));

    expect(await screen.findByRole('alert')).toHaveAttribute(
      'data-error-code',
      ErrorCode.DUPLICATE_EMAIL,
    );
    expect(screen.getByRole('button', { name: '가입하고 시작' })).toBeInTheDocument();
    expect(getAccessToken()).toBeNull();
  });

  it('COMMON_001이면 code로 오류를 표시한다', async () => {
    signupMock.mockRejectedValue(apiFail(ErrorCode.INVALID_INPUT, 400));
    renderAuth('/signup');

    await userEvent.type(await screen.findByLabelText('이름'), '재현');
    await userEvent.type(screen.getByLabelText('이메일'), 'user@example.com');
    await userEvent.type(screen.getByLabelText('비밀번호'), 'password1234');
    await userEvent.click(screen.getByRole('button', { name: '가입하고 시작' }));

    expect(await screen.findByRole('alert')).toHaveAttribute(
      'data-error-code',
      ErrorCode.INVALID_INPUT,
    );
  });
});

describe('보호된 라우트', () => {
  it('세션이 없으면 / 를 /login 으로 보낸다', async () => {
    renderAuth('/');

    expect(await screen.findByRole('button', { name: '로그인' })).toBeInTheDocument();
    expect(screen.queryByText('피드')).not.toBeInTheDocument();
  });

  it('세션이 있으면 피드를 보여준다', async () => {
    renderAuth('/', { authenticated: true });

    expect(await screen.findByText('피드')).toBeInTheDocument();
  });

  it('세션이 있으면 /login 을 홈으로 보낸다', async () => {
    renderAuth('/login', { authenticated: true });

    expect(await screen.findByText('피드')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '로그인' })).not.toBeInTheDocument();
  });

  it('세션 복구가 성공하면 보호된 홈을 보여준다', async () => {
    restoreSessionMock.mockResolvedValue(true);
    renderAuth('/');

    expect(await screen.findByText('피드')).toBeInTheDocument();
  });

  it('초기 재발급 실패는 가입 화면을 로그인으로 바꾸지 않는다', async () => {
    restoreSessionMock.mockImplementation(async () => {
      notifySessionExpired();
      return false;
    });
    renderAuth('/signup');

    expect(await screen.findByRole('button', { name: '가입하고 시작' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '로그인' })).not.toBeInTheDocument();
  });
});

describe('세션 만료', () => {
  it('onSessionExpired가 발생하면 로그인 화면으로 보낸다', async () => {
    renderAuth('/', { authenticated: true });
    expect(await screen.findByText('피드')).toBeInTheDocument();

    notifySessionExpired();

    expect(await screen.findByRole('button', { name: '로그인' })).toBeInTheDocument();
    expect(getAccessToken()).toBeNull();
    expect(screen.queryByText('피드')).not.toBeInTheDocument();
  });
});
