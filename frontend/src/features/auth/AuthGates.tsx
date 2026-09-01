import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { SessionPending, useAuth } from './AuthSession';

/** 로그인된 사용자만 앱 화면을 본다. */
export function RequireAuth() {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'unknown') return <SessionPending />;
  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  return <Outlet />;
}

/** 이미 로그인한 사용자는 가입/로그인 화면을 다시 보지 않는다. */
export function GuestOnly() {
  const { status } = useAuth();

  if (status === 'unknown') return <SessionPending />;
  if (status === 'authenticated') return <Navigate to="/" replace />;
  return <Outlet />;
}
