import React from 'react';
import { render, act, waitFor, fireEvent } from '@testing-library/react-native';
import { AuthProvider, useAuth } from '../AuthContext';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { authService } from '../../services/authService';
import { Text, TouchableOpacity } from 'react-native';
import { ThemeProvider } from '../ThemeContext';

jest.mock('../../services/authService', () => ({
  authService: {
    login: jest.fn(),
    register: jest.fn(),
  },
}));

const TestComponent: React.FC = () => {
  const { user, isAuthenticated, loading, login, register, logout, updateUser } = useAuth();
  return (
    <>
      <Text testID="loading">{String(loading)}</Text>
      <Text testID="authenticated">{String(isAuthenticated)}</Text>
      <Text testID="userName">{user?.name || 'none'}</Text>
      <Text testID="budgetDay">{String(user?.budgetStartDay || 'none')}</Text>
      <TouchableOpacity testID="loginBtn" onPress={() => login('a@b.com', 'pass')} />
      <TouchableOpacity testID="registerBtn" onPress={() => register('Name', 'a@b.com', 'pass')} />
      <TouchableOpacity testID="logoutBtn" onPress={() => logout()} />
      <TouchableOpacity testID="updateBtn" onPress={() => updateUser({ budgetStartDay: 15 })} />
    </>
  );
};

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider><AuthProvider>{children}</AuthProvider></ThemeProvider>
);

describe('AuthContext', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (AsyncStorage.getItem as jest.Mock).mockResolvedValue(null);
  });

  it('starts with loading true and not authenticated', async () => {
    const { getByTestId } = render(<TestComponent />, { wrapper: Wrapper });
    // Initially loading
    await waitFor(() => {
      expect(getByTestId('authenticated').props.children).toBe('false');
    });
  });

  it('loads stored auth on mount', async () => {
    (AsyncStorage.getItem as jest.Mock).mockImplementation((key: string) => {
      if (key === '@oh_churus_token') return Promise.resolve('stored-token');
      if (key === '@oh_churus_user') return Promise.resolve(JSON.stringify({ userId: '1', name: 'Test', email: 'a@b.com' }));
      return Promise.resolve(null);
    });

    const { getByTestId } = render(<TestComponent />, { wrapper: Wrapper });

    await waitFor(() => {
      expect(getByTestId('authenticated').props.children).toBe('true');
      expect(getByTestId('userName').props.children).toBe('Test');
    });
  });

  it('sets budgetStartDay default to 1 when loading stored user', async () => {
    (AsyncStorage.getItem as jest.Mock).mockImplementation((key: string) => {
      if (key === '@oh_churus_token') return Promise.resolve('token');
      if (key === '@oh_churus_user') return Promise.resolve(JSON.stringify({ userId: '1', name: 'T', email: 'e' }));
      return Promise.resolve(null);
    });

    const { getByTestId } = render(<TestComponent />, { wrapper: Wrapper });

    await waitFor(() => {
      expect(getByTestId('budgetDay').props.children).toBe('1');
    });
  });

  it('login stores token and user', async () => {
    (authService.login as jest.Mock).mockResolvedValueOnce({
      correct: true,
      object: { token: 'new-token', userId: '2', name: 'User', email: 'u@u.com' },
    });

    const { getByTestId } = render(<TestComponent />, { wrapper: Wrapper });

    await waitFor(() => expect(getByTestId('loading').props.children).toBe('false'));

    await act(async () => {
      fireEvent.press(getByTestId('loginBtn'));
    });

    await waitFor(() => {
      expect(getByTestId('authenticated').props.children).toBe('true');
      expect(getByTestId('userName').props.children).toBe('User');
    });
    expect(AsyncStorage.setItem).toHaveBeenCalledWith('@oh_churus_token', 'new-token');
  });

  it('register stores token and user', async () => {
    (authService.register as jest.Mock).mockResolvedValueOnce({
      correct: true,
      object: { token: 'reg-token', userId: '3', name: 'New', email: 'n@n.com' },
    });

    const { getByTestId } = render(<TestComponent />, { wrapper: Wrapper });
    await waitFor(() => expect(getByTestId('loading').props.children).toBe('false'));

    await act(async () => {
      fireEvent.press(getByTestId('registerBtn'));
    });

    await waitFor(() => {
      expect(getByTestId('authenticated').props.children).toBe('true');
      expect(getByTestId('userName').props.children).toBe('New');
    });
  });

  it('logout clears token and user', async () => {
    (AsyncStorage.getItem as jest.Mock).mockImplementation((key: string) => {
      if (key === '@oh_churus_token') return Promise.resolve('token');
      if (key === '@oh_churus_user') return Promise.resolve(JSON.stringify({ userId: '1', name: 'T', email: 'e' }));
      return Promise.resolve(null);
    });

    const { getByTestId } = render(<TestComponent />, { wrapper: Wrapper });
    await waitFor(() => expect(getByTestId('authenticated').props.children).toBe('true'));

    await act(async () => {
      fireEvent.press(getByTestId('logoutBtn'));
    });

    await waitFor(() => {
      expect(getByTestId('authenticated').props.children).toBe('false');
      expect(getByTestId('userName').props.children).toBe('none');
    });
    expect(AsyncStorage.removeItem).toHaveBeenCalledWith('@oh_churus_token');
    expect(AsyncStorage.removeItem).toHaveBeenCalledWith('@oh_churus_user');
  });

  it('updateUser merges updates into current user', async () => {
    (AsyncStorage.getItem as jest.Mock).mockImplementation((key: string) => {
      if (key === '@oh_churus_token') return Promise.resolve('token');
      if (key === '@oh_churus_user') return Promise.resolve(JSON.stringify({ userId: '1', name: 'T', email: 'e', budgetStartDay: 1 }));
      return Promise.resolve(null);
    });

    const { getByTestId } = render(<TestComponent />, { wrapper: Wrapper });
    await waitFor(() => expect(getByTestId('authenticated').props.children).toBe('true'));

    await act(async () => {
      fireEvent.press(getByTestId('updateBtn'));
    });

    await waitFor(() => {
      expect(getByTestId('budgetDay').props.children).toBe('15');
    });
  });
});
