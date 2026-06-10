import React from 'react';
import { render, fireEvent } from '@testing-library/react-native';
import PeriodNavigator from '../PeriodNavigator';
import { TestWrapper } from '../../../test-utils';

describe('PeriodNavigator', () => {
  it('renders the month label and period range', () => {
    const { getByText } = render(
      <TestWrapper>
        <PeriodNavigator periodStart="2026-03-15" periodEnd="2026-04-14" onPrevious={jest.fn()} onNext={jest.fn()} />
      </TestWrapper>
    );
    expect(getByText('Marzo 2026')).toBeTruthy();
    expect(getByText('2026-03-15 → 2026-04-14')).toBeTruthy();
  });

  it('calls onPrevious and onNext', () => {
    const onPrevious = jest.fn();
    const onNext = jest.fn();
    const { getByText } = render(
      <TestWrapper>
        <PeriodNavigator periodStart="2026-03-15" periodEnd="2026-04-14" onPrevious={onPrevious} onNext={onNext} />
      </TestWrapper>
    );
    fireEvent.press(getByText('<'));
    fireEvent.press(getByText('>'));
    expect(onPrevious).toHaveBeenCalled();
    expect(onNext).toHaveBeenCalled();
  });

  it('disables next when canGoNext is false', () => {
    const onNext = jest.fn();
    const { getByText } = render(
      <TestWrapper>
        <PeriodNavigator periodStart="2026-03-15" periodEnd="2026-04-14" onPrevious={jest.fn()} onNext={onNext} canGoNext={false} />
      </TestWrapper>
    );
    fireEvent.press(getByText('>'));
    expect(onNext).not.toHaveBeenCalled();
  });
});
