import React from 'react';
import { render } from '@testing-library/react-native';
import ExportImportScreen from '../settings/ExportImportScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';

jest.mock('../../services/api', () => ({
  __esModule: true,
  default: { post: jest.fn() },
}));

jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '1', name: 'A', email: 'a@b.com', budgetStartDay: 1 },
  token: 'tok', loading: false, isAuthenticated: true,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
});

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

describe('ExportImportScreen', () => {
  it('renders export and import sections', () => {
    const { getByText } = render(<ExportImportScreen />, { wrapper: Wrapper });
    expect(getByText(/Exportar Reporte Excel/)).toBeTruthy();
    expect(getByText(/Importar desde Excel/)).toBeTruthy();
  });
});
