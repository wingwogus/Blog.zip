import { useInfiniteQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
import type { CursorPage, FeedItem } from '@/api/types';

/**
 * 커서 기반 무한 스크롤. docs/specs/feed.md 8장
 *
 * 정렬이 publishedAt DESC, postId DESC 안정 정렬이라 중복이나 누락이 없다.
 * nextCursor가 null이면 마지막 페이지다. 커서 값을 클라이언트가 해석하지 않는다.
 */
export const feedQueryKey = (unreadOnly: boolean) => ['feed', { unreadOnly }] as const;

export function useFeed(options: { unreadOnly?: boolean } = {}) {
  const unreadOnly = options.unreadOnly ?? false;

  return useInfiniteQuery({
    queryKey: feedQueryKey(unreadOnly),
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) =>
      api.get<CursorPage<FeedItem>>('/feed', {
        ...(pageParam ? { cursor: pageParam } : {}),
        ...(unreadOnly ? { unreadOnly: true } : {}),
      }),
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
  });
}
