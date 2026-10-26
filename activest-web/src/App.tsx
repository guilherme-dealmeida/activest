import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
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
import { GoalDetailPage } from '@/pages/GoalDetailPage';
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
import { TasksPage } from '@/pages/TasksPage';
import { isLegacyUrl, translateLegacyUrl } from '@/utils/legacyUrl';

/** Tela só de administrador (Tarefas, D14): quem não é volta ao Dashboard. */
const AdminOnly = ({ children }: { children: JSX.Element }) => {
  const { user } = useAuth();
  if (!user) return null;
  return user.admin ? children : <Navigate to="/dashboard" replace />;
};

const ProtectedRoute = () => {
  const { isAuthenticated, isLoading } = useAuth();
  if (isLoading) return <div className="min-h-screen flex items-center justify-center text-white">Carregando...</div>;
  return isAuthenticated ? <Layout /> : <Navigate to="/login" replace />;
};

function App() {
  const { pathname, search, hash } = useLocation();
  // Endereço antigo, em português (rota ou parâmetro): vai para o equivalente em inglês (PLANO_NOMES_EM_INGLES, E4)
  if (isLegacyUrl(pathname, search)) {
    return <Navigate to={translateLegacyUrl(pathname, search) + hash} replace />;
  }
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/signup" element={<SignupPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/terms" element={<LegalPage kind="terms" />} />
      <Route path="/privacy" element={<LegalPage kind="privacy" />} />
      
      <Route element={<ProtectedRoute />}>
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/performance" element={<PerformancePage />} />
        <Route path="/portfolio" element={<PortfolioPage />} />
        <Route path="/allocation" element={<AllocationPage />} />
        <Route path="/strategy" element={<StrategyPage />} />
        <Route path="/rebalance" element={<RebalancePage />} />
        <Route path="/transactions" element={<TransactionsPage />} />
        <Route path="/ceiling-prices" element={<CeilingPricesPage />} />
        <Route path="/ceiling-prices/:ticker" element={<CeilingPriceDetailPage />} />
        <Route path="/assets" element={<IssuerPage />} />
        <Route path="/assets/:ticker" element={<IssuerPage />} />
        <Route path="/indices" element={<IndicesPage />} />
        <Route path="/dividends" element={<DividendsPage />} />
        <Route path="/goals" element={<GoalsPage />} />
        <Route path="/goals/:id" element={<GoalDetailPage />} />
        <Route path="/integrations" element={<IntegrationsPage />} />
        <Route path="/integrations/:id/runs/:runId" element={<IntegrationRunPage />} />
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="/notifications" element={<NotificationsPage />} />
        <Route path="/tasks" element={<AdminOnly><TasksPage /></AdminOnly>} />
      </Route>
    </Routes>
  );
}


export default App;
