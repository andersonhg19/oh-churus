import React from 'react';
import { render, fireEvent } from '@testing-library/react-native';
import DonutChart from '../DonutChart';
import { TestWrapper } from '../../../test-utils';

const data = [
  { id: '1', label: 'Vivienda', value: 1500000, percentage: 60, color: '#FF0000' },
  { id: '2', label: 'Comida', value: 1000000, percentage: 40, color: '#00FF00' },
  { id: '3', label: 'Cero', value: 0, percentage: 0, color: '#0000FF' },
];

describe('DonutChart', () => {
  it('renders legend for non-zero slices and calls onSlicePress', () => {
    const onSlicePress = jest.fn();
    const { getByText, queryByText } = render(
      <TestWrapper>
        <DonutChart data={data} centerLabel="Total" centerValue={2500000} onSlicePress={onSlicePress} />
      </TestWrapper>
    );
    expect(getByText('Vivienda')).toBeTruthy();
    expect(getByText('Comida')).toBeTruthy();
    expect(queryByText('Cero')).toBeNull(); // 0% slice excluded from legend
    fireEvent.press(getByText('Vivienda'));
    expect(onSlicePress).toHaveBeenCalled();
  });

  it('renders in multi-line center mode', () => {
    const { getByText } = render(
      <TestWrapper>
        <DonutChart
          data={data}
          centerLines={[
            { label: 'Ingresos', value: 3000000, color: '#0F0' },
            { label: 'Gastos', value: 2500000, color: '#F00', bold: true },
          ]}
        />
      </TestWrapper>
    );
    expect(getByText('Vivienda')).toBeTruthy();
  });
});
