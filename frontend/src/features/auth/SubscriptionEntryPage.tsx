import { useState, type FormEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '@/api/client';
import { ErrorCode } from '@/api/errorCodes';
import type { BlogLookupResult, SubscriptionCreateResult } from '@/api/types';
import { formErrorFrom, type FormError } from './formError';

export function SubscriptionEntryPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [step, setStep] = useState<'url' | 'name'>('url');
  const [lookup, setLookup] = useState<BlogLookupResult | null>(null);
  const [error, setError] = useState<FormError | null>(null);

  const lookupMutation = useMutation({
    mutationFn: (url: string) => api.post<BlogLookupResult>('/blogs/lookup', { url }),
    onSuccess: (result) => {
      setLookup(result);
      setError(null);
      setStep('name');
    },
    onError: (cause) => setError(formErrorFrom(cause)),
  });
  const createMutation = useMutation({
    mutationFn: (friendName: string) =>
      api.post<SubscriptionCreateResult>('/subscriptions', {
        lookupToken: lookup?.lookupToken,
        friendName,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['feed'] });
      navigate('/', { replace: true });
    },
    onError: (cause) => {
      const formError = formErrorFrom(cause);
      if (formError.code === ErrorCode.BLOG_LOOKUP_EXPIRED) {
        setLookup(null);
        setStep('url');
      }
      setError(formError);
    },
  });

  function submitUrl(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const url = String(new FormData(event.currentTarget).get('url') ?? '').trim();
    setError(null);
    lookupMutation.mutate(url);
  }

  function submitName(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const friendName = String(new FormData(event.currentTarget).get('friendName') ?? '').trim();
    setError(null);
    createMutation.mutate(friendName);
  }

  const busy = lookupMutation.isPending || createMutation.isPending;

  return (
    <div className="mx-auto flex max-w-xl flex-col gap-6 px-4 py-10">
      <div>
        <h1 className="text-[24px] font-bold tracking-tight text-slate-900">
          친구의 블로그를 추가해요
        </h1>
        <p className="mt-2 text-[15px] leading-6 text-slate-500">
          친구의 블로그 주소를 입력하면 새 글을 한곳에서 받아볼 수 있어요.
        </p>
      </div>

      {step === 'url' ? (
        <form onSubmit={submitUrl} className="rounded-xl border border-slate-200 bg-white p-4">
          <label htmlFor="blog-url" className="text-[14px] font-semibold text-slate-800">
            블로그 주소
          </label>
          <input id="blog-url" name="url" type="text" inputMode="url" required
            placeholder="velog.io/@친구" disabled={busy}
            className="mt-2 min-h-11 w-full rounded-lg border border-slate-200 bg-white px-3 text-[15px] text-slate-900 outline-none" />
          {error && <p role="alert" data-error-code={error.code} className="mt-3 text-[13px] text-rose-600">{error.message}</p>}
          <button type="submit" disabled={busy} className="mt-4 min-h-11 w-full rounded-lg bg-slate-900 text-[15px] font-semibold text-white disabled:opacity-50">
            {lookupMutation.isPending ? '확인 중' : '블로그 확인'}
          </button>
        </form>
      ) : lookup ? (
        <div className="flex flex-col gap-4">
          <section className="rounded-xl border border-slate-200 bg-white p-4">
            <h2 className="text-[18px] font-semibold text-slate-900">{lookup.blog.title}</h2>
            <p className="mt-1 text-[13px] text-slate-500">{lookup.blog.platformLabel} · {lookup.blog.siteUrl}</p>
            {lookup.recentPosts.length > 0 && <ul className="mt-4 list-disc pl-5 text-[14px] text-slate-700">{lookup.recentPosts.map((post) => <li key={`${post.title}-${post.publishedAt}`}>{post.title}{post.publishedAt && <time className="ml-2 text-[12px] text-slate-500" dateTime={post.publishedAt}>{new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium' }).format(new Date(post.publishedAt))}</time>}</li>)}</ul>}
          </section>
          {lookup.alreadySubscribed ? (
            <p role="status" className="text-[14px] text-slate-600">이미 {lookup.currentFriendName ?? '이 블로그'}를 추가했어요.</p>
          ) : (
            <form onSubmit={submitName} className="rounded-xl border border-slate-200 bg-white p-4">
              <label htmlFor="friend-name" className="text-[14px] font-semibold text-slate-800">누구의 블로그인가요?</label>
              <input id="friend-name" name="friendName" required minLength={1} maxLength={20} disabled={busy} className="mt-2 min-h-11 w-full rounded-lg border border-slate-200 px-3 text-[15px]" />
              {error && <p role="alert" data-error-code={error.code} className="mt-3 text-[13px] text-rose-600">{error.message}</p>}
              <button type="submit" disabled={busy} className="mt-4 min-h-11 w-full rounded-lg bg-slate-900 text-[15px] font-semibold text-white disabled:opacity-50">{createMutation.isPending ? '추가 중' : '추가하기'}</button>
            </form>
          )}
        </div>
      ) : null}

      <Link
        to="/"
        className="inline-flex min-h-11 w-fit items-center rounded-lg px-1 text-[15px] font-semibold text-slate-700 underline underline-offset-4"
      >
        홈으로 돌아가기
      </Link>
    </div>
  );
}
