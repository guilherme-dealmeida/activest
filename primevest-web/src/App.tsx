import { Routes, Route, Navigate } from 'react-router-dom';
import { Layout } from '@/components/layout/Layout';
import { useAuth } from '@/context/AuthContext';

// Pages
import { DashboardPage } from '@/pages/DashboardPage';
import { PortfolioPage } from '@/pages/PortfolioPage';
import { AllocationPage } from '@/pages/AllocationPage';
import { StrategyPage } from '@/pages/StrategyPage';
import { TransactionsPage } from '@/pages/TransactionsPage';
import { CeilingPricesPage } from '@/pages/CeilingPricesPage';
import { DividendsPage } from '@/pages/DividendsPage';
import { GoalsPage } from '@/pages/GoalsPage';
import { SettingsPage } from '@/pages/SettingsPage';
import { LoginPage } from '@/pages/LoginPage';

const ProtectedRoute = () => {
  const { isAuthenticated, isLoading } = useAuth();
  if (isLoading) return <div className="min-h-screen flex items-center justify-center text-white">Carregando...</div>;
  return isAuthenticated ? <Layout /> : <Navigate to="/login" replace />;
};

function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="/login" element={<LoginPage />} />
      
      <Route element={<ProtectedRoute />}>
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/portfolio" element={<PortfolioPage />} />
        <Route path="/allocation" element={<AllocationPage />} />
        <Route path="/strategy" element={<StrategyPage />} />
        <Route path="/transactions" element={<TransactionsPage />} />
        <Route path="/ceiling-prices" element={<CeilingPricesPage />} />
        <Route path="/dividends" element={<DividendsPage />} />
        <Route path="/goals" element={<GoalsPage />} />
        <Route path="/settings" element={<SettingsPage />} />
      </Route>
    </Routes>
  );
}

export default App;
