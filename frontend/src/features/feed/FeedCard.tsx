import { useState } from 'react';
import type { FeedItem } from '@/api/types';
import { avatarColorOf, initialOf } from '@/lib/avatar';
import { formatRelativeTime } from '@/lib/relativeTime';

interface FeedCardProps {
  item: FeedItem;
  onOpen: (item: FeedItem) => void;
}

/**
 * 피드 카드 하나. frontend/DESIGN.md 2장
 *
 * 카드 전체가 탭 영역이고, 유일한 액션은 원문 이동이다.
 * 좋아요/댓글/공유 액션 바를 만들지 않는다. (PRD 5장 Non-Goals)
 * 본문과 발췌는 표시하지 않는다. 저장하지 않는 데이터다.
 */
export function FeedCard({ item, onOpen }: FeedCardProps) {
  // 원본 이미지가 사라지면 로드에 실패한다. 정상 상태로 처리하고 이미지 영역을 접는다.
  const [thumbnailFailed, setThumbnailFailed] = useState(false);
  const showThumbnail = Boolean(item.thumbnailUrl) && !thumbnailFailed;

  const host = safeHost(item.blog.siteUrl);

  return (
    <article
      className={[
        'overflow-hidden rounded-xl border bg-white transition-colors',
        item.isNew ? 'border-sky-300' : 'border-slate-200',
      ].join(' ')}
    >
      <button
        type="button"
        onClick={() => onOpen(item)}
        className="block w-full cursor-pointer text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-500"
      >
        <header className="flex items-center gap-3 px-4 py-3">
          <span
            aria-hidden="true"
            className={[
              'flex size-9 shrink-0 items-center justify-center rounded-full text-sm font-semibold text-white',
              avatarColorOf(item.friend.subscriptionId),
            ].join(' ')}
          >
            {initialOf(item.friend.friendName)}
          </span>

          <span className="min-w-0 flex-1">
            {/* 화면의 주체는 블로그가 아니라 사람이다. PRD P-001 */}
            <span className="block truncate text-[15px] font-semibold text-slate-900">
              {item.friend.friendName}
            </span>
            {/* 출처를 숨기지 않는다. PRD BR-009 */}
            <span className="block truncate text-[13px] text-slate-500">
              {item.blog.platformLabel}
            </span>
          </span>

          <time
            dateTime={item.publishedAt}
            className="shrink-0 text-[13px] text-slate-500"
          >
            {formatRelativeTime(item.publishedAt, {
              estimated: item.publishedAtEstimated,
            })}
          </time>
        </header>

        {showThumbnail && (
          <div className="aspect-4/5 w-full bg-slate-100">
            <img
              src={item.thumbnailUrl ?? ''}
              alt=""
              loading="lazy"
              onError={() => setThumbnailFailed(true)}
              className="size-full object-cover"
            />
          </div>
        )}

        <div className="px-4 py-3">
          <h2
            className={[
              'text-[17px] font-semibold leading-relaxed',
              item.isRead ? 'text-slate-500' : 'text-slate-900',
            ].join(' ')}
          >
            {item.title}
          </h2>
          <p className="mt-2 flex items-center gap-1 text-[13px] text-slate-500">
            <span className="truncate">{host}</span>
            <span aria-hidden="true">→</span>
          </p>
        </div>
      </button>
    </article>
  );
}

/** 사용자 입력에서 비롯된 URL이므로 파싱 실패를 정상 경로로 다룬다. */
function safeHost(url: string): string {
  try {
    return new URL(url).host.replace(/^www\./, '');
  } catch {
    return url;
  }
}
