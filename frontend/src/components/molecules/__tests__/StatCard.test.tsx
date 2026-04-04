import React from 'react';
import { render, screen } from '@testing-library/react-native';
import StatCard from '../StatCard';
import { TestWrapper } from '../../../test-utils';

const renderStatCard = (props: Partial<React.ComponentProps<typeof StatCard>> = {}) => {
  const defaultProps = {
    title: 'Total Income',
    value: 500000,
    icon: '💰',
    ...props,
  };
  return render(<StatCard {...defaultProps} />, { wrapper: TestWrapper });
};

describe('StatCard', () => {
  it('renders title and formatted currency value', () => {
    renderStatCard();
    expect(screen.getByText('Total Income')).toBeTruthy();
    // es-CO locale may use period or comma as thousand separator
    expect(screen.getByText(/^\$500[.,]000$/)).toBeTruthy();
  });

  it('shows trend percentage when provided (positive)', () => {
    renderStatCard({ trend: 12.5 });
    expect(screen.getByText('+12.5%')).toBeTruthy();
  });

  it('shows trend percentage when provided (negative)', () => {
    renderStatCard({ trend: -5.3 });
    expect(screen.getByText('-5.3%')).toBeTruthy();
  });

  it('does not show trend when not provided', () => {
    const { queryByText } = renderStatCard();
    expect(queryByText(/\d+\.\d+%/)).toBeNull();
  });

  it('renders icon', () => {
    renderStatCard({ icon: '📊' });
    expect(screen.getByText('📊')).toBeTruthy();
  });

  it('renders non-currency value as plain number', () => {
    renderStatCard({ value: 42, isCurrency: false });
    expect(screen.getByText('42')).toBeTruthy();
  });
});
