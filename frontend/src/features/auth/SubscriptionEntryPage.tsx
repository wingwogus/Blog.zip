import { Link } from 'react-router-dom';

/**
 * 가입 직후에도 막히지 않는 친구 블로그 추가 진입점이다.
 * URL 검증과 추가 요청은 issue #3의 구독 플로우에서 연결한다.
 */
export function SubscriptionEntryPage() {
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

      <div className="rounded-xl border border-slate-200 bg-white p-4">
        <label htmlFor="blog-url" className="text-[14px] font-semibold text-slate-800">
          블로그 주소
        </label>
        <input
          id="blog-url"
          type="url"
          inputMode="url"
          placeholder="https://example.com"
          disabled
          className="mt-2 min-h-11 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 text-[15px] text-slate-500 outline-none"
        />
        <p className="mt-3 text-[13px] leading-5 text-slate-500">
          블로그 연결 기능을 준비하고 있어요.
        </p>
      </div>

      <Link
        to="/"
        className="inline-flex min-h-11 w-fit items-center rounded-lg px-1 text-[15px] font-semibold text-slate-700 underline underline-offset-4"
      >
        홈으로 돌아가기
      </Link>
    </div>
  );
}
