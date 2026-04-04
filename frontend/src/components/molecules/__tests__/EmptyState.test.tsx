import React from 'react';
import { render, screen } from '@testing-library/react-native';
import EmptyState from '../EmptyState';
import { TestWrapper } from '../../../test-utils';

const renderEmptyState = (props: Partial<React.ComponentProps<typeof EmptyState>> = {}) => {
  const defaultProps = {
    title: 'No data',
    message: 'Nothing to show here',
    ...props,
  };
  return render(<EmptyState {...defaultProps} />, { wrapper: TestWrapper });
};

describe('EmptyState', () => {
  it('renders title and message', () => {
    renderEmptyState();
    expect(screen.getByText('No data')).toBeTruthy();
    expect(screen.getByText('Nothing to show here')).toBeTruthy();
  });

  it('shows default icon when none provided', () => {
    renderEmptyState();
    expect(screen.getByText('🐿️')).toBeTruthy();
  });

  it('shows custom icon when provided', () => {
    renderEmptyState({ icon: '📭' });
    expect(screen.getByText('📭')).toBeTruthy();
  });
});
