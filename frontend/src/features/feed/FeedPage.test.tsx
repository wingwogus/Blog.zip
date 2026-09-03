import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { api } from '@/api/client';
import { FeedPage } from './FeedPage';

describe('FeedPage', () => {
  it('글이 있어도 친구 블로그 추가 진입점을 보여준다', async () => {
    vi.spyOn(api, 'get').mockResolvedValue({
      items: [{
        postId: 'pst_1', title: '새 글', url: 'https://example.com/post', publishedAt: '2026-01-01T00:00:00Z',
        publishedAtEstimated: false, thumbnailUrl: null, isRead: false, isNew: true,
        friend: { subscriptionId: 'sub_1', friendName: '지민' },
        blog: { id: 'blg_1', title: '친구 블로그', siteUrl: 'https://example.com', platform: 'GENERIC', platformLabel: '개인 블로그' },
      }], nextCursor: null,
    });
    render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MemoryRouter><FeedPage /></MemoryRouter></QueryClientProvider>);
    expect(await screen.findByText('새 글')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '친구 블로그 추가' })).toHaveAttribute('href', '/subscriptions/new');
  });
});
