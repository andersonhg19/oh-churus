import React, { createContext, useContext, useState, useEffect, useCallback, ReactNode } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { authService } from '../services/authService';
import { TOKEN_KEY } from '../services/api';

const USER_KEY = '@oh_churus_user';

interface UserInfo {
  userId: string;
  name: string;
  email: string;
  budgetStartDay: number;
}

interface AuthContextType {
  user: UserInfo | null;
  token: string | null;
  loading: boolean;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (name: string, email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  updateUser: (updates: Partial<UserInfo>) => Promise<void>;
}

const AuthContext = createContext<AuthContextType>({
  user: null,
  token: null,
  loading: true,
  isAuthenticated: false,
  login: async () => {},
  register: async () => {},
  logout: async () => {},
  updateUser: async () => {},
});

export const useAuth = () => useContext(AuthContext);

interface AuthProviderProps {
  children: ReactNode;
}

export const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
  const [user, setUser] = useState<UserInfo | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadStoredAuth = async () => {
      try {
        const [storedToken, storedUser] = await Promise.all([
          AsyncStorage.getItem(TOKEN_KEY),
          AsyncStorage.getItem(USER_KEY),
        ]);
        if (storedToken && storedUser) {
          setToken(storedToken);
          const parsed = JSON.parse(storedUser);
          setUser({ budgetStartDay: 1, ...parsed });

          // Refrescar budgetStartDay desde el backend
          try {
            const userRes = await authService.getUser(parsed.userId);
            if (userRes.correct && userRes.object) {
              const refreshed = { ...parsed, budgetStartDay: userRes.object.budgetStartDay ?? parsed.budgetStartDay ?? 1 };
              setUser(refreshed);
              await AsyncStorage.setItem(USER_KEY, JSON.stringify(refreshed));
            }
          } catch {
            // Usar valor del storage si falla
          }
        }
      } catch {
        // Ignore errors
      } finally {
        setLoading(false);
      }
    };
    loadStoredAuth();
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const result = await authService.login(email, password);
    if (!result.correct) {
      throw new Error(result.message);
    }
    const authData = result.object;
    // Guardar token primero para que el interceptor lo use
    await AsyncStorage.setItem(TOKEN_KEY, authData.token);
    setToken(authData.token);

    // Consultar datos completos del usuario (incluye budgetStartDay)
    let budgetStartDay = 1;
    try {
      const userRes = await authService.getUser(authData.userId);
      if (userRes.correct && userRes.object) {
        budgetStartDay = userRes.object.budgetStartDay ?? 1;
      }
    } catch {
      // Fallback a 1 si falla
    }

    const userInfo: UserInfo = {
      userId: authData.userId,
      name: authData.name,
      email: authData.email,
      budgetStartDay,
    };
    await AsyncStorage.setItem(USER_KEY, JSON.stringify(userInfo));
    setUser(userInfo);
  }, []);

  const register = useCallback(async (name: string, email: string, password: string) => {
    const result = await authService.register({ name, email, password });
    if (!result.correct) {
      throw new Error(result.message);
    }
    const authData = result.object;
    const userInfo: UserInfo = {
      userId: authData.userId,
      name: authData.name,
      email: authData.email,
      budgetStartDay: 1,
    };
    await Promise.all([
      AsyncStorage.setItem(TOKEN_KEY, authData.token),
      AsyncStorage.setItem(USER_KEY, JSON.stringify(userInfo)),
    ]);
    setToken(authData.token);
    setUser(userInfo);
  }, []);

  const logout = useCallback(async () => {
    await Promise.all([
      AsyncStorage.removeItem(TOKEN_KEY),
      AsyncStorage.removeItem(USER_KEY),
    ]);
    setToken(null);
    setUser(null);
  }, []);

  const updateUser = useCallback(async (updates: Partial<UserInfo>) => {
    setUser((prev) => {
      if (!prev) return prev;
      const updated = { ...prev, ...updates };
      AsyncStorage.setItem(USER_KEY, JSON.stringify(updated)).catch(() => {});
      return updated;
    });
  }, []);

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        loading,
        isAuthenticated: !!token && !!user,
        login,
        register,
        logout,
        updateUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};
