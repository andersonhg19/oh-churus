import React from 'react';
import { render, fireEvent, screen } from '@testing-library/react-native';
import Input from '../Input';
import { TestWrapper } from '../../../test-utils';

const renderInput = (props: Partial<React.ComponentProps<typeof Input>> = {}) => {
  const defaultProps = {
    label: 'Email',
    value: '',
    onChangeText: jest.fn(),
    ...props,
  };
  return render(<Input {...defaultProps} />, { wrapper: TestWrapper });
};

describe('Input', () => {
  it('renders with label', () => {
    renderInput({ label: 'Username' });
    expect(screen.getByText('Username')).toBeTruthy();
  });

  it('calls onChangeText when text changes', () => {
    const onChangeText = jest.fn();
    renderInput({ onChangeText, placeholder: 'Type here' });
    const input = screen.getByPlaceholderText('Type here');
    fireEvent.changeText(input, 'hello');
    expect(onChangeText).toHaveBeenCalledWith('hello');
  });

  it('shows error message when error prop is provided', () => {
    renderInput({ error: 'Field is required' });
    expect(screen.getByText('Field is required')).toBeTruthy();
  });

  it('does not show error text when no error', () => {
    const { queryByText } = renderInput();
    expect(queryByText('Field is required')).toBeNull();
  });

  it('handles secureTextEntry prop', () => {
    renderInput({ secureTextEntry: true, placeholder: 'Password' });
    const input = screen.getByPlaceholderText('Password');
    expect(input.props.secureTextEntry).toBe(true);
  });
});
