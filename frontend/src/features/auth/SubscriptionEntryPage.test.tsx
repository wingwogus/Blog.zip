import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { vi } from 'vitest';
import { api } from '@/api/client';
import { SubscriptionEntryPage } from './SubscriptionEntryPage';

function renderPage(queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })) {
  return render(<QueryClientProvider client={queryClient}><MemoryRouter initialEntries={['/subscriptions/new']}><Routes><Route path="/subscriptions/new" element={<SubscriptionEntryPage />} /><Route path="/" element={<div data-testid="home">홈</div>} /></Routes></MemoryRouter></QueryClientProvider>);
}

describe('친구 블로그 추가 진입 화면', () => {
  it('가입 직후 친구 블로그 추가 화면을 열 수 있다', () => {
    renderPage();
    expect(screen.getByRole('heading', { name: '친구의 블로그를 추가해요' })).toBeInTheDocument();
    expect(screen.getByLabelText('블로그 주소')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '홈으로 돌아가기' })).toHaveAttribute('href', '/');
  });

  it('조회 성공 후 이름을 입력해 추가하고 홈으로 이동한다', async () => {
    const user = userEvent.setup();
    const post = vi.spyOn(api, 'post')
      .mockResolvedValueOnce({ blog: { title: '친구 블로그', siteUrl: 'https://example.com', platform: 'GENERIC', platformLabel: '개인 블로그' }, recentPosts: [], alreadySubscribed: false, currentFriendName: null, lookupToken: 'token' })
      .mockResolvedValueOnce({ id: 'sub-1', friendName: '지민', blog: { id: 'blog-1', title: '친구 블로그', siteUrl: 'https://example.com', platform: 'GENERIC', platformLabel: '개인 블로그' }, createdAt: '2026-01-01T00:00:00Z' });
    renderPage();
    await user.type(screen.getByLabelText('블로그 주소'), 'velog.io/@wingwogus');
    await user.click(screen.getByRole('button', { name: '블로그 확인' }));
    expect(await screen.findByLabelText('누구의 블로그인가요?')).toBeInTheDocument();
    await user.type(screen.getByLabelText('누구의 블로그인가요?'), '지민');
    await user.click(screen.getByRole('button', { name: '추가하기' }));
    await waitFor(() => expect(screen.getByTestId('home')).toBeInTheDocument());
    expect(post).toHaveBeenNthCalledWith(1, '/blogs/lookup', { url: 'velog.io/@wingwogus' });
    expect(post).toHaveBeenNthCalledWith(2, '/subscriptions', { lookupToken: 'token', friendName: '지민' });
  });

  it('조회 API 오류를 표시한다', async () => {
    const user = userEvent.setup();
    vi.spyOn(api, 'post').mockRejectedValueOnce(new Error('주소를 확인해 주세요.'));
    renderPage();
    await user.type(screen.getByLabelText('블로그 주소'), 'https://bad.example');
    await user.click(screen.getByRole('button', { name: '블로그 확인' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('주소를 확인해 주세요.');
  });

  it('추가 후 피드 캐시를 무효화한다', async () => {
    const user = userEvent.setup();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
    vi.spyOn(api, 'post')
      .mockResolvedValueOnce({ blog: { title: '친구 블로그', siteUrl: 'https://example.com', platform: 'GENERIC', platformLabel: '개인 블로그' }, recentPosts: [], alreadySubscribed: false, currentFriendName: null, lookupToken: 'token' })
      .mockResolvedValueOnce({ id: 'sub-1', friendName: '지민', blog: { id: 'blog-1', title: '친구 블로그', siteUrl: 'https://example.com', platform: 'GENERIC', platformLabel: '개인 블로그' }, createdAt: '2026-01-01T00:00:00Z' });
    renderPage(queryClient);
    await user.type(screen.getByLabelText('블로그 주소'), 'example.com');
    await user.click(screen.getByRole('button', { name: '블로그 확인' }));
    await user.type(await screen.findByLabelText('누구의 블로그인가요?'), '지민');
    await user.click(screen.getByRole('button', { name: '추가하기' }));
    await waitFor(() => expect(invalidate).toHaveBeenCalledWith({ queryKey: ['feed'] }));
  });

  it('최근 글의 게시 날짜를 표시한다', async () => {
    const user = userEvent.setup();
    vi.spyOn(api, 'post').mockResolvedValueOnce({
      blog: { title: '친구 블로그', siteUrl: 'https://example.com', platform: 'GENERIC', platformLabel: '개인 블로그' },
      recentPosts: [{ title: '첫 글', publishedAt: '2026-01-02T00:00:00Z' }], alreadySubscribed: true,
      currentFriendName: '지민', lookupToken: 'token',
    });
    renderPage();
    await user.type(screen.getByLabelText('블로그 주소'), 'example.com');
    await user.click(screen.getByRole('button', { name: '블로그 확인' }));
    expect(await screen.findByText('첫 글')).toBeInTheDocument();
    expect(screen.getByText(/2026/)).toBeInTheDocument();
  });
});
