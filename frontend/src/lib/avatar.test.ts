import { describe, expect, it } from 'vitest';
import { avatarColorOf, initialOf } from './avatar';

describe('initialOf', () => {
  it('첫 글자를 반환한다', () => {
    expect(initialOf('지훈')).toBe('지');
  });

  it('앞뒤 공백을 무시한다', () => {
    expect(initialOf('  재현 ')).toBe('재');
  });

  it('이모지를 한 글자로 다룬다', () => {
    // 서로게이트 페어가 쪼개지면 깨진 문자가 렌더된다.
    expect(initialOf('👋친구')).toBe('👋');
  });

  it('빈 문자열은 물음표로 대체한다', () => {
    expect(initialOf('   ')).toBe('?');
  });
});

describe('avatarColorOf', () => {
  it('같은 seed는 항상 같은 색이다', () => {
    // 같은 친구가 스크롤 중 색이 바뀌면 안 된다.
    expect(avatarColorOf('sub_01H')).toBe(avatarColorOf('sub_01H'));
  });

  it('항상 팔레트 안의 클래스를 반환한다', () => {
    for (const seed of ['a', 'sub_01HZZZ', '', '지훈', '👋']) {
      expect(avatarColorOf(seed)).toMatch(/^bg-[a-z]+-500$/);
    }
  });

  it('서로 다른 seed가 색을 분산시킨다', () => {
    const colors = new Set(
      Array.from({ length: 40 }, (_, i) => avatarColorOf(`sub_${i}`)),
    );
    expect(colors.size).toBeGreaterThan(1);
  });
});
