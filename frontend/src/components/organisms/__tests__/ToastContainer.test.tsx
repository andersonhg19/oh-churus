import React from 'react';
import { render } from '@testing-library/react-native';

// Local override of the global useToast stub so the container has toasts to render.
jest.mock('../../../contexts/ToastContext', () => ({
  __esModule: true,
  useToast: () => ({
    toasts: [
      { id: 't1', type: 'success', title: 'Guardado', message: 'ok', duration: 3000 },
      { id: 't2', type: 'error', title: 'Error', duration: 5000 },
    ],
    hideToast: jest.fn(),
  }),
}));

import { ToastContainer } from '../ToastContainer';
import { TestWrapper } from '../../../test-utils';

describe('ToastContainer', () => {
  it('renders all active toasts', () => {
    const { getByText } = render(
      <TestWrapper><ToastContainer /></TestWrapper>
    );
    expect(getByText('Guardado')).toBeTruthy();
    expect(getByText('Error')).toBeTruthy();
  });
});
