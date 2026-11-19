import React from 'react';
import { BrowserRouter, Routes, Route, useLocation } from 'react-router-dom';
import { ThemeProvider } from './context/ThemeContext';
import { AuthProvider } from './context/AuthContext';
import { Navbar } from './components/common/Navbar';
import { Footer } from './components/common/Footer';
import { LandingPage } from './pages/LandingPage';
import { CustomerPage } from './pages/CustomerPage';
import { StaffLoginPage } from './pages/StaffLoginPage';
import { StaffDashboardPage } from './pages/StaffDashboardPage';
import { AdminLoginPage } from './pages/AdminLoginPage';
import { AdminDashboardPage } from './pages/AdminDashboardPage';
import { DisplayBoardPage } from './pages/DisplayBoardPage';

// Layout wrapper to conditionally hide Navbar and Footer on full-screen TV display board
const AppLayout: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const location = useLocation();
  const isDisplayBoard = location.pathname === '/display';

  if (isDisplayBoard) {
    return <main className="min-h-screen">{children}</main>;
  }

  return (
    <div className="min-h-screen flex flex-col bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-100 transition-colors">
      <Navbar />
      <main className="flex-1">
        {children}
      </main>
      <Footer />
    </div>
  );
};

export const App: React.FC = () => {
  return (
    <ThemeProvider>
      <AuthProvider>
        <BrowserRouter>
          <AppLayout>
            <Routes>
              <Route path="/" element={<LandingPage />} />
              <Route path="/customer" element={<CustomerPage />} />
              <Route path="/staff/login" element={<StaffLoginPage />} />
              <Route path="/staff" element={<StaffDashboardPage />} />
              <Route path="/admin/login" element={<AdminLoginPage />} />
              <Route path="/admin" element={<AdminDashboardPage />} />
              <Route path="/display" element={<DisplayBoardPage />} />
              {/* Fallback to Home */}
              <Route path="*" element={<LandingPage />} />
            </Routes>
          </AppLayout>
        </BrowserRouter>
      </AuthProvider>
    </ThemeProvider>
  );
};

export default App;
