import React from 'react';
import { render } from '@testing-library/react-native';
import BudgetVsReal from '../BudgetVsReal';
import { TestWrapper } from '../../../test-utils';

const categories: any = [
  { categoryId: 'c1', categoryName: 'Arriendo', categoryType: 'EXPENSE', total: 1500000, color: '#FF0000' },
  { categoryId: 'c2', categoryName: 'Mercado', categoryType: 'EXPENSE', total: 800000, color: '#00FF00' },
  { categoryId: 'c3', categoryName: 'Salario', categoryType: 'INCOME', total: 3000000, color: '#0000FF' },
];

describe('BudgetVsReal', () => {
  it('renders only expense categories with title and legend', () => {
    const { getByText, queryByText } = render(
      <TestWrapper>
        <BudgetVsReal categories={categories} budgetByCategory={{ c1: 1000000, c2: 900000 }} />
      </TestWrapper>
    );
    expect(getByText('Presupuesto vs Real')).toBeTruthy();
    expect(getByText('Arriendo')).toBeTruthy();
    expect(getByText('Mercado')).toBeTruthy();
    // income category excluded
    expect(queryByText('Salario')).toBeNull();
    expect(getByText('Excedido')).toBeTruthy();
  });

  it('returns null when there are no expense categories', () => {
    const { toJSON } = render(
      <TestWrapper>
        <BudgetVsReal categories={[categories[2]]} budgetByCategory={{}} />
      </TestWrapper>
    );
    expect(toJSON()).toBeNull();
  });
});
