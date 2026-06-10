import React from 'react';
import { render, fireEvent } from '@testing-library/react-native';
import CenterFAB from '../CenterFAB';
import { TestWrapper } from '../../../test-utils';

describe('CenterFAB', () => {
  it('renders the + icon and calls onPress', () => {
    const onPress = jest.fn();
    const { getByText } = render(
      <TestWrapper><CenterFAB onPress={onPress} /></TestWrapper>
    );
    const plus = getByText('+');
    expect(plus).toBeTruthy();
    fireEvent.press(plus);
    expect(onPress).toHaveBeenCalled();
  });
});
