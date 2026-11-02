import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { Layout } from '@/components/layout/Layout';
import { useAuth } from '@/context/AuthContext';

// Pages
import { LoginPage } from '@/pages/LoginPage';
import { SignupPage } from '@/pages/SignupPage';
import { ForgotPasswordPage } from '@/pages/ForgotPasswordPage';
import { LegalPage } from '@/pages/LegalPage';
import { isLegacyUrl, translateLegacyUrl } from '@/utils/legacyUrl';
import { lazyPage } from '@/utils/lazyPage';

// Telas internas sob demanda (docs/planos/PLANO_PERFORMANCE_TELAS.md, D3); login, cadastro, senha e termos
// continuam no pacote inicial (porta de entrada). O Suspense fica no Layout, em volta do Outlet.
const DashboardPage = lazyPage(() => import('@/pages/DashboardPage'), 'DashboardPage');
const PerformancePage = lazyPage(() => import('@/pages/PerformancePage'), 'PerformancePage');
const PortfolioPage = lazyPage(() => import('@/pages/PortfolioPage'), 'PortfolioPage');
const AllocationPage = lazyPage(() => import('@/pages/AllocationPage'), 'AllocationPage');
const StrategyPage = lazyPage(() => import('@/pages/StrategyPage'), 'StrategyPage');
const RebalancePage = lazyPage(() => import('@/pages/RebalancePage'), 'RebalancePage');
const TransactionsPage = lazyPage(() => import('@/pages/TransactionsPage'), 'TransactionsPage');
const CeilingPricesPage = lazyPage(() => import('@/pages/CeilingPricesPage'), 'CeilingPricesPage');
const CeilingPriceDetailPage = lazyPage(() => import('@/pages/CeilingPriceDetailPage'), 'CeilingPriceDetailPage');
const DividendsPage = lazyPage(() => import('@/pages/DividendsPage'), 'DividendsPage');
const GoalsPage = lazyPage(() => import('@/pages/GoalsPage'), 'GoalsPage');
const GoalDetailPage = lazyPage(() => import('@/pages/GoalDetailPage'), 'GoalDetailPage');
const IntegrationsPage = lazyPage(() => import('@/pages/IntegrationsPage'), 'IntegrationsPage');
const IntegrationRunPage = lazyPage(() => import('@/pages/IntegrationRunPage'), 'IntegrationRunPage');
const SettingsPage = lazyPage(() => import('@/pages/SettingsPage'), 'SettingsPage');
const NotificationsPage = lazyPage(() => import('@/pages/NotificationsPage'), 'NotificationsPage');
const IssuerPage = lazyPage(() => import('@/pages/IssuerPage'), 'IssuerPage');
const IndicesPage = lazyPage(() => import('@/pages/IndicesPage'), 'IndicesPage');
const TasksPage = lazyPage(() => import('@/pages/TasksPage'), 'TasksPage');
const CurrenciesPage = lazyPage(() => import('@/pages/CurrenciesPage'), 'CurrenciesPage');

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
        <Route path="/currencies" element={<AdminOnly><CurrenciesPage /></AdminOnly>} />
      </Route>
    </Routes>
  );
}


export default App;
