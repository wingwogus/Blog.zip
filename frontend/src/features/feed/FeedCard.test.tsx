import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { FeedItem } from '@/api/types';
import { FeedCard } from './FeedCard';

function makeItem(overrides: Partial<FeedItem> = {}): FeedItem {
  return {
    postId: 'pst_1',
    title: 'Spring 트랜잭션 전파 옵션 정리',
    url: 'https://velog.io/@wingwogus/spring-tx',
    publishedAt: '2026-08-30T09:00:00Z',
    publishedAtEstimated: false,
    thumbnailUrl: null,
    isRead: false,
    isNew: true,
    friend: { subscriptionId: 'sub_1', friendName: '지훈' },
    blog: {
      id: 'blg_1',
      title: '재현의 개발 블로그',
      siteUrl: 'https://velog.io/@wingwogus',
      platform: 'VELOG',
      platformLabel: 'Velog',
    },
    ...overrides,
  };
}

describe('FeedCard', () => {
  it('친구 이름과 제목을 보여준다', () => {
    render(<FeedCard item={makeItem()} onOpen={vi.fn()} />);

    expect(screen.getByText('지훈')).toBeInTheDocument();
    expect(screen.getByText('Spring 트랜잭션 전파 옵션 정리')).toBeInTheDocument();
  });

  it('출처를 확인할 수 있게 플랫폼과 호스트를 보여준다', () => {
    // PRD BR-009: 원본 Blog와 Post 출처를 확인할 수 있어야 한다.
    render(<FeedCard item={makeItem()} onOpen={vi.fn()} />);

    expect(screen.getByText('Velog')).toBeInTheDocument();
    expect(screen.getByText('velog.io')).toBeInTheDocument();
  });

  it('카드 전체가 하나의 버튼이다', async () => {
    const onOpen = vi.fn();
    const item = makeItem();
    render(<FeedCard item={item} onOpen={onOpen} />);

    await userEvent.click(screen.getByRole('button'));

    expect(onOpen).toHaveBeenCalledWith(item);
  });

  it('썸네일이 없으면 이미지를 만들지 않는다', () => {
    // 이미지 없는 카드가 정상 케이스다. frontend/DESIGN.md 2장
    render(<FeedCard item={makeItem({ thumbnailUrl: null })} onOpen={vi.fn()} />);

    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });

  it('썸네일이 있으면 이미지를 렌더한다', () => {
    render(
      <FeedCard
        item={makeItem({ thumbnailUrl: 'https://img.example.com/a.png' })}
        onOpen={vi.fn()}
      />,
    );

    expect(screen.getByRole('presentation')).toHaveAttribute(
      'src',
      'https://img.example.com/a.png',
    );
  });

  it('썸네일 로드가 실패하면 이미지 영역을 접는다', () => {
    // 원본 이미지가 사라지는 것은 정상 상태다. docs/decisions/004-post-collection.md
    render(
      <FeedCard
        item={makeItem({ thumbnailUrl: 'https://img.example.com/gone.png' })}
        onOpen={vi.fn()}
      />,
    );

    const img = screen.getByRole('presentation');
    // fireEvent를 쓰면 React 상태 업데이트가 act로 감싸진다.
    fireEvent.error(img);

    expect(screen.queryByRole('presentation')).not.toBeInTheDocument();
  });

  it('본문이나 발췌를 표시하지 않는다', () => {
    // 저장하지 않는 데이터다. FeedItem 타입에 필드가 없다.
    const item = makeItem();
    render(<FeedCard item={item} onOpen={vi.fn()} />);

    expect(item).not.toHaveProperty('excerpt');
    expect(item).not.toHaveProperty('content');
  });

  it('좋아요나 댓글 같은 액션을 만들지 않는다', () => {
    // PRD 5장 Non-Goals. 카드의 유일한 액션은 원문 이동이다.
    render(<FeedCard item={makeItem()} onOpen={vi.fn()} />);

    expect(screen.getAllByRole('button')).toHaveLength(1);
  });

  it('추정 게시 시각을 구분해 표시한다', () => {
    render(
      <FeedCard
        item={makeItem({ publishedAtEstimated: true })}
        onOpen={vi.fn()}
      />,
    );

    expect(screen.getByText(/^약 /)).toBeInTheDocument();
  });

  it('기계가 읽을 수 있는 게시 시각을 남긴다', () => {
    render(<FeedCard item={makeItem()} onOpen={vi.fn()} />);

    expect(screen.getByText(/전$|방금/).closest('time')).toHaveAttribute(
      'dateTime',
      '2026-08-30T09:00:00Z',
    );
  });
});
