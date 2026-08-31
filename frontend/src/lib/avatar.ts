/**
 * 친구 식별 표시. frontend/DESIGN.md 6장
 *
 * Blog.zip의 친구는 Blog.zip 사용자가 아니므로(PRD P-004) 프로필 이미지가 없다.
 * 이름 이니셜 + 안정적인 배경색으로 대체한다.
 *
 * 색은 subscriptionId로 결정한다. 같은 친구가 항상 같은 색을 갖는다.
 */

/** 카드 배경과 대비가 확보되는 톤만 쓴다. */
const PALETTE = [
  'bg-rose-500',
  'bg-orange-500',
  'bg-amber-500',
  'bg-emerald-500',
  'bg-teal-500',
  'bg-sky-500',
  'bg-indigo-500',
  'bg-violet-500',
  'bg-fuchsia-500',
] as const;

export function initialOf(friendName: string): string {
  const trimmed = friendName.trim();
  if (!trimmed) return '?';
  // 이모지와 결합 문자를 한 글자로 다룬다. 'A'.split('')로는 깨진다.
  return [...trimmed][0] ?? '?';
}

export function avatarColorOf(seed: string): string {
  let hash = 0;
  for (let i = 0; i < seed.length; i += 1) {
    hash = (hash * 31 + seed.charCodeAt(i)) | 0;
  }
  const index = Math.abs(hash) % PALETTE.length;
  return PALETTE[index] as string;
}
