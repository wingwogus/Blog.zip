/**
 * 피드 카드에 쓰는 상대 시간. frontend/DESIGN.md 2장
 *
 * publishedAtEstimated가 true면 원본이 게시 시각을 주지 않아 수집 시각으로 대체된 값이다.
 * 그 경우 "약"을 붙여 추정임을 드러낸다. docs/specs/feed.md FR-005
 */
const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

export function formatRelativeTime(
  iso: string,
  options: { estimated?: boolean; now?: Date } = {},
): string {
  const target = new Date(iso);
  if (Number.isNaN(target.getTime())) return '';

  const now = options.now ?? new Date();
  const diff = now.getTime() - target.getTime();
  const prefix = options.estimated ? '약 ' : '';

  // 미래 시각은 서버가 수집 시각으로 보정하지만, 시계 차이로 약간 앞설 수 있다.
  if (diff < MINUTE) return `${prefix}방금`;
  if (diff < HOUR) return `${prefix}${Math.floor(diff / MINUTE)}분 전`;
  if (diff < DAY) return `${prefix}${Math.floor(diff / HOUR)}시간 전`;
  if (diff < 7 * DAY) return `${prefix}${Math.floor(diff / DAY)}일 전`;

  const sameYear = target.getFullYear() === now.getFullYear();
  return new Intl.DateTimeFormat('ko-KR', {
    ...(sameYear ? {} : { year: 'numeric' }),
    month: 'long',
    day: 'numeric',
  }).format(target);
}
