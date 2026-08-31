import { describe, expect, it } from 'vitest';
import { formatRelativeTime } from './relativeTime';

// 시간을 주입해 검증한다. 실제 시계에 의존하지 않는다.
const now = new Date('2026-08-30T12:00:00Z');

describe('formatRelativeTime', () => {
  it('1분 이내는 방금으로 표시한다', () => {
    expect(formatRelativeTime('2026-08-30T11:59:30Z', { now })).toBe('방금');
  });

  it('분 단위를 표시한다', () => {
    expect(formatRelativeTime('2026-08-30T11:30:00Z', { now })).toBe('30분 전');
  });

  it('시간 단위를 표시한다', () => {
    expect(formatRelativeTime('2026-08-30T09:00:00Z', { now })).toBe('3시간 전');
  });

  it('일 단위를 표시한다', () => {
    expect(formatRelativeTime('2026-08-28T12:00:00Z', { now })).toBe('2일 전');
  });

  it('7일이 지나면 날짜로 표시한다', () => {
    expect(formatRelativeTime('2026-08-01T12:00:00Z', { now })).toContain('8월');
  });

  it('해가 다르면 연도를 포함한다', () => {
    expect(formatRelativeTime('2025-12-25T12:00:00Z', { now })).toContain('2025');
  });

  it('추정 시각에는 약을 붙인다', () => {
    // 원본이 게시 시각을 주지 않은 경우다. docs/specs/feed.md FR-005
    expect(formatRelativeTime('2026-08-30T09:00:00Z', { now, estimated: true })).toBe(
      '약 3시간 전',
    );
  });

  it('시계 차이로 미래인 값도 깨지지 않는다', () => {
    expect(formatRelativeTime('2026-08-30T12:00:30Z', { now })).toBe('방금');
  });

  it('해석할 수 없는 값은 빈 문자열이다', () => {
    expect(formatRelativeTime('not-a-date', { now })).toBe('');
  });
});
