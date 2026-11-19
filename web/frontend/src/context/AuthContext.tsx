import React, { createContext, useContext, useEffect, useState } from 'react';
import { AuthUser, UserRole } from '../types';
import { api, getErrorMessage } from '../services/api';

interface AuthContextType {
  user: AuthUser | null;
  loading: boolean;
  login: (role: 'STAFF' | 'ADMIN', username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  error: string | null;
  clearError: () => void;
}

const AuthContext = createContext<AuthContextType>({
  user: null,
  loading: true,
  login: async () => {},
  logout: async () => {},
  error: null,
  clearError: () => {}
});

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // Attempt session restoration via httpOnly cookie
    api.getCurrentUser()
      .then((u) => {
        setUser(u);
      })
      .catch(() => {
        setUser(null);
      })
      .finally(() => {
        setLoading(false);
      });
  }, []);

  const login = async (role: 'STAFF' | 'ADMIN', username: string, password: string) => {
    setError(null);
    try {
      const u = role === 'STAFF' 
        ? await api.staffLogin(username, password)
        : await api.adminLogin(username, password);
      setUser(u);
    } catch (err) {
      const msg = getErrorMessage(err);
      setError(msg);
      throw new Error(msg);
    }
  };

  const logout = async () => {
    try {
      await api.logout();
    } finally {
      setUser(null);
    }
  };

  const clearError = () => setError(null);

  return (
    <AuthContext.Provider value={{ user, loading, login, logout, error, clearError }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
