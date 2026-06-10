import React from 'react';
import { render, fireEvent } from '@testing-library/react-native';
import { Toast } from '../Toast';
import { TestWrapper } from '../../../test-utils';
import type { ToastType } from '../../../contexts/ToastContext';

describe('Toast', () => {
  it('renders title and message and calls onDismiss when pressed', () => {
    const onDismiss = jest.fn();
    const { getByText } = render(
      <TestWrapper>
        <Toast type="success" title="Guardado" message="Todo bien" onDismiss={onDismiss} />
      </TestWrapper>
    );
    expect(getByText('Guardado')).toBeTruthy();
    expect(getByText('Todo bien')).toBeTruthy();
    fireEvent.press(getByText('Guardado'));
    expect(onDismiss).toHaveBeenCalled();
  });

  it('renders without a message', () => {
    const { getByText, queryByText } = render(
      <TestWrapper>
        <Toast type="info" title="Info" onDismiss={jest.fn()} />
      </TestWrapper>
    );
    expect(getByText('Info')).toBeTruthy();
    expect(queryByText('Todo bien')).toBeNull();
  });

  it.each(['success', 'error', 'warning', 'info'] as ToastType[])('renders the %s variant', (type) => {
    const { getByText } = render(
      <TestWrapper>
        <Toast type={type} title={`T-${type}`} onDismiss={jest.fn()} />
      </TestWrapper>
    );
    expect(getByText(`T-${type}`)).toBeTruthy();
  });
});
