import { useLocation } from 'react-router-dom';
import { useTheme } from '@/context/ThemeContext';
import { clsx } from 'clsx';

const routeNames: Record<string, string> = {
  '/dashboard': 'Dashboard',
  '/portfolio': 'Carteira',
  '/allocation': 'Alocação',
  '/strategy': 'Estratégia de Investimentos',
  '/transactions': 'Transações',
  '/ceiling-prices': 'Preço Teto',
  '/emissoras': 'Emissoras',
  '/indices': 'Índices',
  '/dividends': 'Dividendos e Proventos',
  '/goals': 'Minhas Metas',
  '/integrations': 'Integrações',
  '/settings': 'Configurações',
};

export const Header = () => {
  const location = useLocation();
  const { isDark } = useTheme();
  const currentPath = Object.keys(routeNames).find(path => location.pathname.startsWith(path));
  const title = currentPath ? routeNames[currentPath] : '';

  return (
    <header className={clsx(
      "h-16 border-b flex items-center px-6 transition-colors duration-300",
      isDark 
        ? "bg-[#111111] border-white/10" 
        : "bg-white border-gray-200"
    )}>
      <h2 className={clsx(
        "text-xl font-semibold",
        isDark ? "text-white" : "text-gray-900"
      )}>{title}</h2>
    </header>
  );
};
