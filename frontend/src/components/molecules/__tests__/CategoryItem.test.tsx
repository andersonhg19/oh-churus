import React from 'react';
import { render, fireEvent } from '@testing-library/react-native';
import CategoryItem from '../CategoryItem';
import { TestWrapper } from '../../../test-utils';
import { CategoryTree } from '../../../types';

const baseCategory: CategoryTree = {
  id: '1',
  userId: 'u1',
  name: 'Salario',
  type: 'INCOME',
  icon: 'wallet',
  color: '#4CAF50',
  children: [],
};

describe('CategoryItem', () => {
  it('renders category name', () => {
    const { getByText } = render(
      <TestWrapper><CategoryItem category={baseCategory} /></TestWrapper>
    );
    expect(getByText('Salario')).toBeTruthy();
  });

  it('renders category type in Spanish', () => {
    const { getByText } = render(
      <TestWrapper><CategoryItem category={baseCategory} /></TestWrapper>
    );
    expect(getByText('Ingreso')).toBeTruthy();
  });

  it('calls onPress when pressed', () => {
    const onPress = jest.fn();
    const { getByText } = render(
      <TestWrapper><CategoryItem category={baseCategory} onPress={onPress} /></TestWrapper>
    );
    fireEvent.press(getByText('Salario'));
    expect(onPress).toHaveBeenCalled();
  });

  it('shows expand button when category has children', () => {
    const withChildren: CategoryTree = {
      ...baseCategory,
      children: [{ ...baseCategory, id: '2', name: 'Child' }],
    };
    const { getByText } = render(
      <TestWrapper><CategoryItem category={withChildren} onToggle={jest.fn()} /></TestWrapper>
    );
    expect(getByText('+')).toBeTruthy();
  });

  it('shows minus sign when expanded', () => {
    const withChildren: CategoryTree = {
      ...baseCategory,
      children: [{ ...baseCategory, id: '2', name: 'Child' }],
    };
    const { getByText } = render(
      <TestWrapper><CategoryItem category={withChildren} expanded onToggle={jest.fn()} /></TestWrapper>
    );
    expect(getByText('-')).toBeTruthy();
  });

  it('does not show expand button when no children', () => {
    const { queryByText } = render(
      <TestWrapper><CategoryItem category={baseCategory} /></TestWrapper>
    );
    expect(queryByText('+')).toBeNull();
    expect(queryByText('-')).toBeNull();
  });

  it('applies depth indentation', () => {
    const { toJSON } = render(
      <TestWrapper><CategoryItem category={baseCategory} depth={2} /></TestWrapper>
    );
    expect(toJSON()).toBeTruthy();
  });
});
