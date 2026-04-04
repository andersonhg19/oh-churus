import React from 'react';
import { render, fireEvent, screen } from '@testing-library/react-native';
import Button from '../Button';
import { TestWrapper } from '../../../test-utils';

const renderButton = (props: Partial<React.ComponentProps<typeof Button>> = {}) => {
  const defaultProps = {
    title: 'Press me',
    onPress: jest.fn(),
    ...props,
  };
  return render(<Button {...defaultProps} />, { wrapper: TestWrapper });
};

describe('Button', () => {
  it('renders with title text', () => {
    renderButton({ title: 'Submit' });
    expect(screen.getByText('Submit')).toBeTruthy();
  });

  it('calls onPress when pressed', () => {
    const onPress = jest.fn();
    renderButton({ onPress });
    fireEvent.press(screen.getByText('Press me'));
    expect(onPress).toHaveBeenCalledTimes(1);
  });

  it('shows loading spinner when loading=true and hides title', () => {
    const { queryByText } = renderButton({ loading: true });
    expect(queryByText('Press me')).toBeNull();
  });

  it('does not call onPress when disabled', () => {
    const onPress = jest.fn();
    renderButton({ onPress, disabled: true });
    fireEvent.press(screen.getByText('Press me'));
    expect(onPress).not.toHaveBeenCalled();
  });

  it('renders primary variant by default', () => {
    renderButton({ variant: 'primary' });
    expect(screen.getByText('Press me')).toBeTruthy();
  });

  it('renders outline variant', () => {
    renderButton({ variant: 'outline' });
    expect(screen.getByText('Press me')).toBeTruthy();
  });

  it('renders danger variant', () => {
    renderButton({ variant: 'danger' });
    expect(screen.getByText('Press me')).toBeTruthy();
  });
});
