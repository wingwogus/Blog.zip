import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { ApiError } from '@/api/ApiError';
import { ErrorCode } from '@/api/errorCodes';
import { FeedPage } from '@/features/feed/FeedPage';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60_000,
      // 인증 실패와 요청 제한은 재시도해도 의미가 없다.
      // 클라이언트가 이미 자동 재발급을 처리한다. docs/decisions/002-auth-strategy.md
      retry: (failureCount, error) => {
        if (error instanceof ApiError) {
          const noRetry: string[] = [
            ErrorCode.UNAUTHORIZED,
            ErrorCode.FORBIDDEN,
            ErrorCode.TOO_MANY_REQUESTS,
            ErrorCode.INVALID_REFRESH_TOKEN,
          ];
          if (noRetry.includes(error.code)) return false;
          if (error.status >= 400 && error.status < 500) return false;
        }
        return failureCount < 2;
      },
    },
  },
});

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <div className="min-h-dvh bg-slate-50">
          <header className="border-b border-slate-200 bg-white">
            <div className="mx-auto max-w-xl px-4 py-3">
              <span className="text-[17px] font-semibold text-slate-900">Blog.zip</span>
            </div>
          </header>
          <main>
            <Routes>
              <Route path="/" element={<FeedPage />} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </main>
        </div>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
