import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { login } from './authApi';
import { AuthAlert, AuthField, AuthScreen, AuthSubmit } from './AuthScreen';
import { useAuth } from './AuthSession';
import { formErrorFrom, type FormError } from './formError';

export function LoginPage() {
  const { establishSession } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<FormError | null>(null);

  const from =
    (location.state as { from?: { pathname?: string } } | null)?.from?.pathname ?? '/';

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const email = String(form.get('email') ?? '').trim();
    const password = String(form.get('password') ?? '');

    setError(null);
    setPending(true);
    try {
      const result = await login({ email, password });
      establishSession(result.accessToken);
      navigate(from === '/login' || from === '/signup' ? '/' : from, { replace: true });
    } catch (cause) {
      setError(formErrorFrom(cause));
    } finally {
      setPending(false);
    }
  }

  return (
    <AuthScreen
      title={
        <>
          <span className="block">친구의 글을</span>
          <span className="mt-1 block pl-8">한곳에 모아 두세요.</span>
        </>
      }
    >
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <AuthField
          className={error ? 'border-rose-300' : 'border-slate-200'}
          delayClassName="auth-rise auth-rise-delay-2"
          label="이메일"
          name="email"
          type="email"
          autoComplete="email"
          required
          maxLength={254}
          autoFocus
        />
        <AuthField
          className={error ? 'border-rose-300' : 'border-slate-200'}
          delayClassName="auth-rise auth-rise-delay-3"
          label="비밀번호"
          name="password"
          type="password"
          autoComplete="current-password"
          required
          minLength={8}
          maxLength={64}
        />
        {error && (
          <div className="auth-rise">
            <AuthAlert code={error.code} message={error.message} />
          </div>
        )}
        <div className="auth-rise auth-rise-delay-4 mt-2">
          <AuthSubmit pending={pending}>로그인</AuthSubmit>
        </div>
      </form>
      <p className="auth-rise auth-rise-delay-5 mt-6 text-[13px] text-slate-500">
        계정이 없다면{' '}
        <Link to="/signup" className="font-semibold text-slate-900">
          가입
        </Link>
      </p>
    </AuthScreen>
  );
}
