import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { signup } from './authApi';
import { AuthAlert, AuthField, AuthScreen, AuthSubmit } from './AuthScreen';
import { useAuth } from './AuthSession';
import { formErrorFrom, type FormError } from './formError';

export function SignupPage() {
  const { establishSession } = useAuth();
  const navigate = useNavigate();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<FormError | null>(null);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const email = String(form.get('email') ?? '').trim();
    const password = String(form.get('password') ?? '');
    const nickname = String(form.get('nickname') ?? '').trim();

    setError(null);
    setPending(true);
    try {
      const result = await signup({ email, password, nickname });
      establishSession(result.accessToken);
      navigate('/', { replace: true });
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
          <span className="block">바로 시작하세요.</span>
          <span className="mt-1 block pl-8">초대는 필요 없습니다.</span>
        </>
      }
    >
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <AuthField
          className={error ? 'border-rose-300' : 'border-slate-200'}
          delayClassName="auth-rise auth-rise-delay-2"
          label="이름"
          name="nickname"
          type="text"
          autoComplete="nickname"
          required
          minLength={1}
          maxLength={20}
          autoFocus
        />
        <AuthField
          className={error ? 'border-rose-300' : 'border-slate-200'}
          delayClassName="auth-rise auth-rise-delay-3"
          label="이메일"
          name="email"
          type="email"
          autoComplete="email"
          required
          maxLength={254}
        />
        <AuthField
          className={error ? 'border-rose-300' : 'border-slate-200'}
          delayClassName="auth-rise auth-rise-delay-4"
          label="비밀번호"
          name="password"
          type="password"
          autoComplete="new-password"
          required
          minLength={8}
          maxLength={64}
        />
        {error && (
          <div className="auth-rise">
            <AuthAlert code={error.code} message={error.message} />
          </div>
        )}
        <div className="auth-rise auth-rise-delay-5 mt-2">
          <AuthSubmit pending={pending}>가입하고 시작</AuthSubmit>
        </div>
      </form>
      <p className="mt-6 text-[13px] text-slate-500">
        이미 계정이 있다면{' '}
        <Link to="/login" className="font-semibold text-slate-900">
          로그인
        </Link>
      </p>
    </AuthScreen>
  );
}
