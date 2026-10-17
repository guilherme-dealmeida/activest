import { Routes, Route, Navigate } from 'react-router-dom';
import { Layout } from '@/components/layout/Layout';
import { useAuth } from '@/context/AuthContext';

// Pages
import { DashboardPage } from '@/pages/DashboardPage';
import { PerformancePage } from '@/pages/PerformancePage';
import { PortfolioPage } from '@/pages/PortfolioPage';
import { AllocationPage } from '@/pages/AllocationPage';
import { StrategyPage } from '@/pages/StrategyPage';
import { RebalancePage } from '@/pages/RebalancePage';
import { TransactionsPage } from '@/pages/TransactionsPage';
import { CeilingPricesPage } from '@/pages/CeilingPricesPage';
import { CeilingPriceDetailPage } from '@/pages/CeilingPriceDetailPage';
import { DividendsPage } from '@/pages/DividendsPage';
import { GoalsPage } from '@/pages/GoalsPage';
import { IntegrationsPage } from '@/pages/IntegrationsPage';
import { IntegrationRunPage } from '@/pages/IntegrationRunPage';
import { SettingsPage } from '@/pages/SettingsPage';
import { NotificationsPage } from '@/pages/NotificationsPage';
import { IssuerPage } from '@/pages/IssuerPage';
import { IndicesPage } from '@/pages/IndicesPage';
import { LoginPage } from '@/pages/LoginPage';
import { SignupPage } from '@/pages/SignupPage';
import { ForgotPasswordPage } from '@/pages/ForgotPasswordPage';
import { LegalPage } from '@/pages/LegalPage';

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
      <Route path="/cadastro" element={<SignupPage />} />
      <Route path="/esqueci-senha" element={<ForgotPasswordPage />} />
      <Route path="/termos" element={<LegalPage kind="termos" />} />
      <Route path="/privacidade" element={<LegalPage kind="privacidade" />} />
      
      <Route element={<ProtectedRoute />}>
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/rentabilidade" element={<PerformancePage />} />
        <Route path="/portfolio" element={<PortfolioPage />} />
        <Route path="/allocation" element={<AllocationPage />} />
        <Route path="/strategy" element={<StrategyPage />} />
        <Route path="/rebalance" element={<RebalancePage />} />
        <Route path="/transactions" element={<TransactionsPage />} />
        <Route path="/ceiling-prices" element={<CeilingPricesPage />} />
        <Route path="/ceiling-prices/:ticker" element={<CeilingPriceDetailPage />} />
        <Route path="/emissoras" element={<IssuerPage />} />
        <Route path="/emissoras/:ticker" element={<IssuerPage />} />
        <Route path="/indices" element={<IndicesPage />} />
        <Route path="/dividends" element={<DividendsPage />} />
        <Route path="/goals" element={<GoalsPage />} />
        <Route path="/integrations" element={<IntegrationsPage />} />
        <Route path="/integrations/:id/runs/:runId" element={<IntegrationRunPage />} />
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="/notifications" element={<NotificationsPage />} />
      </Route>
    </Routes>
  );
}

export default App;
