import '@testing-library/jest-dom/vitest';
import { afterEach, beforeEach, vi } from 'vitest';
import { cleanup } from '@testing-library/react';
import { resetRefreshState } from '@/api/client';
import { clearAccessToken } from '@/api/accessToken';
import { resetSessionRestore } from '@/features/auth/authApi';

beforeEach(() => {
  // 모듈 스코프 상태가 테스트 간에 새지 않게 한다.
  resetRefreshState();
  resetSessionRestore();
  clearAccessToken();
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

// jsdom에는 IntersectionObserver가 없다. 무한 스크롤 감시에 쓰인다.
if (!('IntersectionObserver' in globalThis)) {
  class MockIntersectionObserver implements IntersectionObserver {
    readonly root = null;
    readonly rootMargin = '';
    readonly thresholds: ReadonlyArray<number> = [];
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
    takeRecords(): IntersectionObserverEntry[] {
      return [];
    }
  }
  vi.stubGlobal('IntersectionObserver', MockIntersectionObserver);
}
