import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { getAccessToken, onSessionExpired, setAccessToken } from '@/api/accessToken';
import { markSessionEstablished, resetSessionRestore, restoreSession } from './authApi';

export type AuthStatus = 'unknown' | 'authenticated' | 'anonymous';

interface AuthContextValue {
  status: AuthStatus;
  establishSession: (accessToken: string) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * 세션 상태. accessToken은 메모리에만 둔다.
 * 재발급 실패 시 onSessionExpired로 로그인 화면으로 보낸다.
 * docs/decisions/002-auth-strategy.md
 */
export function AuthSession({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('unknown');
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const generationRef = useRef(0);
  const bootingRef = useRef(true);

  useEffect(() => {
    const generation = generationRef.current;

    if (getAccessToken()) {
      markSessionEstablished();
      bootingRef.current = false;
      setStatus('authenticated');
      return;
    }

    let cancelled = false;
    void restoreSession()
      .then((ok) => {
        if (cancelled || generation !== generationRef.current) return;
        setStatus(ok ? 'authenticated' : 'anonymous');
      })
      .finally(() => {
        if (!cancelled) bootingRef.current = false;
      });

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    return onSessionExpired(() => {
      generationRef.current += 1;
      resetSessionRestore();
      queryClient.clear();
      setStatus('anonymous');
      // 첫 로드의 재발급 실패는 게스트 진입이다. /signup 에 있는 사용자를 쫓아내지 않는다.
      if (!bootingRef.current) {
        navigate('/login', { replace: true });
      }
    });
  }, [navigate, queryClient]);

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      establishSession: (accessToken: string) => {
        generationRef.current += 1;
        bootingRef.current = false;
        markSessionEstablished();
        setAccessToken(accessToken);
        setStatus('authenticated');
      },
    }),
    [status],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error('useAuth는 AuthSession 안에서만 쓸 수 있습니다.');
  }
  return value;
}

export function SessionPending() {
  return (
    <div className="min-h-dvh bg-slate-50" aria-busy="true" aria-label="불러오는 중" />
  );
}
