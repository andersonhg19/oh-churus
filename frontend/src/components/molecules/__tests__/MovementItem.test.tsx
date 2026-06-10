import React from 'react';
import { render, fireEvent } from '@testing-library/react-native';
import MovementItem from '../MovementItem';
import { TestWrapper } from '../../../test-utils';
import { Movement } from '../../../types';

const baseMovement: Movement = {
  id: '1',
  userId: 'u1',
  categoryId: 'c1',
  categoryName: 'Salario',
  categoryType: 'INCOME',
  amount: 3500000,
  description: 'Pago mensual',
  date: '2026-03-01',
  confirmed: true,
};

describe('MovementItem', () => {
  it('renders movement description', () => {
    const { getByText } = render(
      <TestWrapper><MovementItem movement={baseMovement} /></TestWrapper>
    );
    expect(getByText('Pago mensual')).toBeTruthy();
  });

  it('renders category name in caption', () => {
    const { getByText } = render(
      <TestWrapper><MovementItem movement={baseMovement} /></TestWrapper>
    );
    expect(getByText(/Salario/)).toBeTruthy();
  });

  it('shows + sign for income', () => {
    const { getByText } = render(
      <TestWrapper><MovementItem movement={baseMovement} /></TestWrapper>
    );
    expect(getByText(/^\+/)).toBeTruthy();
  });

  it('shows - sign for expense', () => {
    const expense: Movement = { ...baseMovement, categoryType: 'EXPENSE' };
    const { getByText } = render(
      <TestWrapper><MovementItem movement={expense} /></TestWrapper>
    );
    expect(getByText(/^-/)).toBeTruthy();
  });

  it('calls onPress when pressed', () => {
    const onPress = jest.fn();
    const { getByText } = render(
      <TestWrapper><MovementItem movement={baseMovement} onPress={onPress} /></TestWrapper>
    );
    fireEvent.press(getByText('Pago mensual'));
    expect(onPress).toHaveBeenCalled();
  });

  it('shows confirm button for unconfirmed movements', () => {
    const pending: Movement = { ...baseMovement, confirmed: false };
    const onConfirm = jest.fn();
    const { getAllByText } = render(
      <TestWrapper><MovementItem movement={pending} onConfirm={onConfirm} /></TestWrapper>
    );
    // "Confirmar" appears both in the swipe action and in the inline button
    expect(getAllByText('Confirmar').length).toBeGreaterThan(0);
  });

  it('calls onConfirm when confirm button pressed', () => {
    const pending: Movement = { ...baseMovement, confirmed: false };
    const onConfirm = jest.fn();
    const { getAllByText } = render(
      <TestWrapper><MovementItem movement={pending} onConfirm={onConfirm} /></TestWrapper>
    );
    // The inline confirm button is the last "Confirmar" element
    const buttons = getAllByText('Confirmar');
    fireEvent(buttons[buttons.length - 1], 'press', { stopPropagation: jest.fn() });
    expect(onConfirm).toHaveBeenCalled();
  });

  it('does not show confirm button for confirmed movements', () => {
    const { queryByText } = render(
      <TestWrapper><MovementItem movement={baseMovement} /></TestWrapper>
    );
    expect(queryByText('Confirmar')).toBeNull();
  });

  it('falls back to categoryName when no description', () => {
    const noDesc: Movement = { ...baseMovement, description: undefined };
    const { getByText } = render(
      <TestWrapper><MovementItem movement={noDesc} /></TestWrapper>
    );
    expect(getByText('Salario')).toBeTruthy();
  });
});
