import React from 'react';
import { render, screen } from '@testing-library/react-native';
import Badge from '../Badge';
import { TestWrapper } from '../../../test-utils';

const renderBadge = (props: Partial<React.ComponentProps<typeof Badge>> = {}) => {
  const defaultProps = { count: 5, ...props };
  return render(<Badge {...defaultProps} />, { wrapper: TestWrapper });
};

describe('Badge', () => {
  it('renders count number', () => {
    renderBadge({ count: 3 });
    expect(screen.getByText('3')).toBeTruthy();
  });

  it('renders 99+ when count exceeds 99', () => {
    renderBadge({ count: 150 });
    expect(screen.getByText('99+')).toBeTruthy();
  });

  it('renders with small size', () => {
    renderBadge({ count: 1, size: 'small' });
    expect(screen.getByText('1')).toBeTruthy();
  });

  it('renders with medium size (default)', () => {
    renderBadge({ count: 7 });
    expect(screen.getByText('7')).toBeTruthy();
  });

  it('renders with large size', () => {
    renderBadge({ count: 42, size: 'large' });
    expect(screen.getByText('42')).toBeTruthy();
  });
});
