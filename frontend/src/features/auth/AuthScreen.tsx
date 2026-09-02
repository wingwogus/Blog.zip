import type { InputHTMLAttributes, ReactNode } from 'react';

interface AuthScreenProps {
  eyebrow?: string;
  title: ReactNode;
  children: ReactNode;
}

/**
 * 가입/로그인 편집 화면. frontend/DESIGN.md 8장
 *
 * 중앙 카드가 아니라 Feed와 같은 왼쪽 흘림이다.
 */
export function AuthScreen({ eyebrow = 'Blog.zip', title, children }: AuthScreenProps) {
  return (
    <div className="relative min-h-dvh overflow-hidden bg-slate-50">
      <p
        aria-hidden="true"
        className="pointer-events-none absolute -bottom-10 -right-6 rotate-[-8deg] select-none font-display text-[120px] leading-none text-slate-200"
      >
        .zip
      </p>
      <div className="mx-auto flex min-h-dvh max-w-xl flex-col px-4 py-16">
        <p className="auth-rise text-[13px] text-slate-500">{eyebrow}</p>
        <h1 className="auth-rise auth-rise-delay-1 mt-4 font-display text-[28px] leading-[1.3] text-slate-900">
          {title}
        </h1>
        <div className="relative mt-10">{children}</div>
      </div>
    </div>
  );
}

interface AuthFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  delayClassName?: string;
}

export function AuthField({
  label,
  delayClassName,
  id,
  className,
  ...inputProps
}: AuthFieldProps) {
  const inputId = id ?? inputProps.name;

  return (
    <label className={['block', delayClassName].filter(Boolean).join(' ')}>
      <span className="text-[15px] font-semibold text-slate-900">{label}</span>
      <input
        id={inputId}
        className={[
          'mt-2 min-h-11 w-full rounded-lg border bg-white px-4 text-[15px] text-slate-900',
          'placeholder:text-slate-400',
          'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-500',
          className ?? 'border-slate-200',
        ].join(' ')}
        {...inputProps}
      />
    </label>
  );
}

export function AuthSubmit({
  children,
  pending,
}: {
  children: ReactNode;
  pending: boolean;
}) {
  return (
    <button
      type="submit"
      disabled={pending}
      className="inline-flex min-h-11 w-full items-center justify-center rounded-lg bg-slate-900 px-4 text-[15px] font-semibold text-white disabled:opacity-50"
    >
      {pending ? '처리 중' : children}
    </button>
  );
}

export function AuthAlert({ code, message }: { code: string; message: string }) {
  return (
    <p role="alert" data-error-code={code} className="text-[13px] text-rose-600">
      {message}
    </p>
  );
}
