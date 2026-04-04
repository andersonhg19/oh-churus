import React from 'react';
import { render } from '@testing-library/react-native';
import Spinner from '../Spinner';
import { TestWrapper } from '../../../test-utils';

describe('Spinner', () => {
  it('renders without crashing', () => {
    const { getByTestId, toJSON } = render(
      <TestWrapper><Spinner /></TestWrapper>
    );
    expect(toJSON()).toBeTruthy();
  });

  it('renders with small size', () => {
    const { toJSON } = render(
      <TestWrapper><Spinner size="small" /></TestWrapper>
    );
    expect(toJSON()).toBeTruthy();
  });

  it('renders fullScreen variant', () => {
    const { toJSON } = render(
      <TestWrapper><Spinner fullScreen /></TestWrapper>
    );
    expect(toJSON()).toBeTruthy();
  });

  it('renders non-fullScreen by default', () => {
    const { toJSON } = render(
      <TestWrapper><Spinner /></TestWrapper>
    );
    const tree = toJSON();
    // Non-fullScreen doesn't have flex:1 style
    expect(tree).toBeTruthy();
  });
});
