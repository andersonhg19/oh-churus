import React from 'react';
import { render, fireEvent } from '@testing-library/react-native';
import CapsuleToggle from '../CapsuleToggle';
import { TestWrapper } from '../../../test-utils';

const options = [
  { label: 'Gasto', value: 'EXPENSE', color: '#FF0000' },
  { label: 'Ingreso', value: 'INCOME', color: '#00FF00' },
];

describe('CapsuleToggle', () => {
  it('renders all options', () => {
    const { getByText } = render(
      <TestWrapper><CapsuleToggle options={options} selected="EXPENSE" onChange={jest.fn()} /></TestWrapper>
    );
    expect(getByText('Gasto')).toBeTruthy();
    expect(getByText('Ingreso')).toBeTruthy();
  });

  it('calls onChange when an option is pressed', () => {
    const onChange = jest.fn();
    const { getByText } = render(
      <TestWrapper><CapsuleToggle options={options} selected="EXPENSE" onChange={onChange} /></TestWrapper>
    );
    fireEvent.press(getByText('Ingreso'));
    expect(onChange).toHaveBeenCalledWith('INCOME');
  });

  it('handles a single-option toggle (no slider animation branch)', () => {
    const { getByText } = render(
      <TestWrapper><CapsuleToggle options={[options[0]]} selected="EXPENSE" onChange={jest.fn()} /></TestWrapper>
    );
    expect(getByText('Gasto')).toBeTruthy();
  });

  it('falls back to first option when selected is unknown', () => {
    const { getByText } = render(
      <TestWrapper><CapsuleToggle options={options} selected="UNKNOWN" onChange={jest.fn()} /></TestWrapper>
    );
    expect(getByText('Gasto')).toBeTruthy();
  });
});
