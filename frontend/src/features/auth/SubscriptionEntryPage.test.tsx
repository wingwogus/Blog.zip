import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { SubscriptionEntryPage } from './SubscriptionEntryPage';

describe('친구 블로그 추가 진입 화면', () => {
  it('가입 직후 친구 블로그 추가 화면을 열 수 있다', () => {
    render(
      <MemoryRouter>
        <SubscriptionEntryPage />
      </MemoryRouter>,
    );

    expect(
      screen.getByRole('heading', { name: '친구의 블로그를 추가해요' }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText('블로그 주소')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '홈으로 돌아가기' })).toHaveAttribute(
      'href',
      '/',
    );
  });
});
