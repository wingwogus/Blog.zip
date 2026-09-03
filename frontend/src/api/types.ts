/**
 * API 응답 타입. docs/specs/README.md 공통 API 규약과 각 Spec 8장에 대응한다.
 */

/** 모든 응답을 감싸는 래퍼. docs/decisions/010-api-response-contract.md */
export interface ApiEnvelope<T> {
  success: boolean;
  data: T | null;
  error: ApiErrorBody | null;
}

export interface ApiErrorBody {
  code: string;
  messageKey: string;
  message: string;
  detail?: unknown;
}

/** 커서 기반 목록. nextCursor가 null이면 마지막 페이지다. */
export interface CursorPage<T> {
  items: T[];
  nextCursor: string | null;
}

export type Platform = 'NAVER' | 'VELOG' | 'TISTORY' | 'GENERIC';

export interface BlogSummary {
  id: string;
  title: string;
  siteUrl: string;
  platform: Platform;
  platformLabel: string;
}

export interface BlogLookup {
  title: string;
  siteUrl: string;
  platform: Platform;
  platformLabel: string;
}

export interface BlogLookupResult {
  blog: BlogLookup;
  recentPosts: Array<{ title: string; publishedAt: string }>;
  alreadySubscribed: boolean;
  currentFriendName: string | null;
  lookupToken: string;
}

export interface SubscriptionCreateResult {
  id: string;
  friendName: string;
  blog: BlogSummary;
  createdAt: string;
}

/**
 * 피드 항목. docs/specs/feed.md 8장.
 *
 * 본문과 발췌 필드는 없다. 저장하지 않는다. docs/decisions/004-post-collection.md
 */
export interface FeedItem {
  postId: string;
  title: string;
  url: string;
  publishedAt: string;
  /** 원본이 게시 시각을 주지 않아 수집 시각으로 대체한 경우 true */
  publishedAtEstimated: boolean;
  thumbnailUrl: string | null;
  isRead: boolean;
  isNew: boolean;
  friend: {
    subscriptionId: string;
    /** 요청한 사용자가 지정한 이름. 전역 표시명이 아니다. PRD BR-004 */
    friendName: string;
  };
  blog: BlogSummary;
}

export type FetchStatus = 'ACTIVE' | 'FAILING' | 'UNAVAILABLE';

/** 친구 목록 항목. docs/specs/subscription-management.md 8장 */
export interface SubscriptionItem {
  subscriptionId: string;
  friendName: string;
  blog: BlogSummary;
  lastPostPublishedAt: string | null;
  unreadCount: number;
  fetchStatus: FetchStatus;
  lastSuccessfulFetchAt: string | null;
  createdAt: string;
}

export interface AuthUser {
  id: string;
  email: string;
  nickname: string;
  createdAt: string;
}

/** refreshToken은 HttpOnly 쿠키로 오므로 본문에 없다. docs/decisions/002-auth-strategy.md */
export interface LoginResult {
  user: AuthUser;
  accessToken: string;
}

export interface RefreshResult {
  accessToken: string;
}
