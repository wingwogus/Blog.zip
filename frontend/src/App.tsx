import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Navigate, Outlet, Route, Routes } from 'react-router-dom';
import { ApiError } from '@/api/ApiError';
import { ErrorCode } from '@/api/errorCodes';
import { GuestOnly, RequireAuth } from '@/features/auth/AuthGates';
import { AuthSession } from '@/features/auth/AuthSession';
import { LoginPage } from '@/features/auth/LoginPage';
import { SignupPage } from '@/features/auth/SignupPage';
import { SubscriptionEntryPage } from '@/features/auth/SubscriptionEntryPage';
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
        <AuthSession>
          <Routes>
            <Route element={<GuestOnly />}>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/signup" element={<SignupPage />} />
            </Route>
            <Route element={<RequireAuth />}>
              <Route path="/" element={<AppShell />}>
                <Route path="/" element={<FeedPage />} />
                <Route path="/subscriptions/new" element={<SubscriptionEntryPage />} />
              </Route>
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </AuthSession>
      </BrowserRouter>
    </QueryClientProvider>
  );
}

function AppShell() {
  return (
    <div className="min-h-dvh bg-slate-50">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto max-w-xl px-4 py-3">
          <span className="text-[17px] font-semibold text-slate-900">Blog.zip</span>
        </div>
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  );
}
