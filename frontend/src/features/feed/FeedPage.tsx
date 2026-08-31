import { useCallback, useEffect, useRef } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import type { FeedItem } from '@/api/types';
import { FeedCard } from './FeedCard';
import { feedQueryKey, useFeed } from './useFeed';

/**
 * 카드 스크롤 피드. frontend/DESIGN.md
 *
 * 원문은 새 탭으로 열고 앱 내부에서 렌더링하지 않는다. docs/specs/feed.md FR-010
 * 새 탭이므로 이 화면은 언마운트되지 않고 스크롤 위치가 유지된다.
 */
export function FeedPage() {
  const queryClient = useQueryClient();
  const feed = useFeed();
  const sentinelRef = useRef<HTMLDivElement | null>(null);

  const markAsRead = useMutation({
    mutationFn: (postId: string) => api.post<void>(`/posts/${postId}/read`),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: feedQueryKey(false) });
    },
  });

  const openPost = useCallback(
    (item: FeedItem) => {
      // 원문으로 이동하면 읽음으로 처리한다. docs/specs/feed.md FR-009
      if (!item.isRead) markAsRead.mutate(item.postId);
      window.open(item.url, '_blank', 'noopener,noreferrer');
    },
    [markAsRead],
  );

  // 하단에 닿기 전에 다음 페이지를 미리 불러온다. 스크롤이 멈추는 순간을 만들지 않는다.
  const { fetchNextPage, hasNextPage, isFetchingNextPage } = feed;
  useEffect(() => {
    const sentinel = sentinelRef.current;
    if (!sentinel || !hasNextPage) return;

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting) && !isFetchingNextPage) {
          void fetchNextPage();
        }
      },
      { rootMargin: '400px' },
    );
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [fetchNextPage, hasNextPage, isFetchingNextPage]);

  if (feed.isPending) return <FeedSkeleton />;

  if (feed.isError) {
    return (
      <StatusPanel
        title="새 글을 가져올 수 없습니다"
        description="잠시 후 다시 시도해 주세요."
        action={{ label: '다시 시도', onClick: () => void feed.refetch() }}
      />
    );
  }

  const items = feed.data.pages.flatMap((page) => page.items);

  // 구독이 0개인 사용자가 처음 보는 화면이다. 여기서 막히면 안 된다. PRD P-005
  if (items.length === 0) {
    return (
      <StatusPanel
        title="아직 받아볼 글이 없어요"
        description="친구의 블로그 주소를 추가하면 새 글이 여기에 모여요."
        action={{ label: '친구 블로그 추가', href: '/subscriptions/new' }}
      />
    );
  }

  return (
    <div className="mx-auto max-w-xl px-4 py-4">
      <ul className="flex flex-col gap-3">
        {items.map((item) => (
          <li key={item.postId}>
            <FeedCard item={item} onOpen={openPost} />
          </li>
        ))}
      </ul>

      <div ref={sentinelRef} aria-hidden="true" className="h-1" />

      {isFetchingNextPage && (
        <p className="py-4 text-center text-[13px] text-slate-500">불러오는 중</p>
      )}
    </div>
  );
}

function FeedSkeleton() {
  return (
    <div className="mx-auto max-w-xl px-4 py-4" aria-busy="true" aria-label="불러오는 중">
      <div className="flex flex-col gap-3">
        {[0, 1, 2].map((i) => (
          <div key={i} className="rounded-xl border border-slate-200 bg-white">
            <div className="flex items-center gap-3 px-4 py-3">
              <div className="size-9 shrink-0 rounded-full bg-slate-200" />
              <div className="flex-1">
                <div className="h-3.5 w-24 rounded bg-slate-200" />
                <div className="mt-2 h-3 w-16 rounded bg-slate-100" />
              </div>
            </div>
            <div className="aspect-4/5 w-full bg-slate-100" />
            <div className="px-4 py-3">
              <div className="h-4 w-3/4 rounded bg-slate-200" />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

interface StatusPanelProps {
  title: string;
  description: string;
  action?: { label: string; onClick?: () => void; href?: string };
}

function StatusPanel({ title, description, action }: StatusPanelProps) {
  return (
    <div className="mx-auto flex max-w-xl flex-col items-center gap-2 px-4 py-16 text-center">
      <h2 className="text-[17px] font-semibold text-slate-900">{title}</h2>
      <p className="text-[15px] text-slate-500">{description}</p>
      {action && (
        <div className="mt-4">
          {action.href ? (
            <a
              href={action.href}
              className="inline-flex min-h-11 items-center rounded-lg bg-slate-900 px-4 text-[15px] font-semibold text-white"
            >
              {action.label}
            </a>
          ) : (
            <button
              type="button"
              onClick={action.onClick}
              className="inline-flex min-h-11 items-center rounded-lg bg-slate-900 px-4 text-[15px] font-semibold text-white"
            >
              {action.label}
            </button>
          )}
        </div>
      )}
    </div>
  );
}
