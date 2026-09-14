import { NavLink, useLocation, useNavigate } from 'react-router-dom';
import { 
  LayoutDashboard, 
  Briefcase, 
  PieChart, 
  Target, 
  ArrowLeftRight, 
  TrendingDown, 
  DollarSign, 
  Flag,
  LogOut,
  Settings,
  Sun,
  Moon
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { useTheme } from '@/context/ThemeContext';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

const navItems = [
  { name: 'Dashboard', path: '/dashboard', icon: LayoutDashboard },
  { name: 'Carteira', path: '/portfolio', icon: Briefcase },
  { name: 'Alocação', path: '/allocation', icon: PieChart },
  { name: 'Estratégia', path: '/strategy', icon: Target },
  { name: 'Transações', path: '/transactions', icon: ArrowLeftRight },
  { name: 'Preço Teto', path: '/ceiling-prices', icon: TrendingDown },
  { name: 'Dividendos', path: '/dividends', icon: DollarSign },
  { name: 'Metas', path: '/goals', icon: Flag },
];

export const Sidebar = () => {
  const { user, logout } = useAuth();
  const { toggleTheme, isDark } = useTheme();
  const location = useLocation();
  const navigate = useNavigate();

  return (
    <div className={clsx(
      "w-64 flex flex-col shrink-0 h-full border-r transition-colors duration-300",
      isDark 
        ? "bg-[#111111] border-white/10" 
        : "bg-white border-gray-200"
    )}>
      {/* Logo */}
      <div className={clsx(
        "flex flex-col items-center justify-center px-4 py-5 border-b transition-colors duration-300",
        isDark ? "border-white/10" : "border-gray-200"
      )}>
        <h1 className={clsx("text-2xl font-bold tracking-tight", isDark ? "text-white" : "text-gray-900")}>
          Prime<span className="text-accent">Vest</span>
        </h1>
        <p className={clsx("text-[10px] uppercase tracking-[0.2em] mt-1", isDark ? "text-gray-500" : "text-gray-400")}>
          Disciplina leva à liberdade
        </p>
      </div>

      <nav className="flex-1 px-4 py-6 space-y-1 overflow-y-auto">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = location.pathname.startsWith(item.path);
          
          return (
            <NavLink
              key={item.path}
              to={item.path}
              className={twMerge(
                clsx(
                  "group flex items-center px-3 py-2.5 text-sm font-medium rounded-md transition-colors",
                  isActive
                    ? isDark 
                      ? "bg-white/10 text-accent" 
                      : "bg-accent/10 text-accent"
                    : isDark
                      ? "text-gray-300 hover:bg-white/5 hover:text-white"
                      : "text-gray-600 hover:bg-gray-100 hover:text-gray-900"
                )
              )}
            >
              <Icon 
                className={clsx(
                  "mr-3 flex-shrink-0 h-5 w-5", 
                  isActive 
                    ? "text-accent" 
                    : isDark 
                      ? "text-gray-400 group-hover:text-gray-300" 
                      : "text-gray-400 group-hover:text-gray-600"
                )} 
              />
              {item.name}
            </NavLink>
          );
        })}
      </nav>

      <div className={clsx(
        "p-4 border-t transition-colors duration-300",
        isDark ? "border-white/10" : "border-gray-200"
      )}>
        <div className="flex items-center">
          <div className="h-8 w-8 rounded-full bg-accent/20 flex items-center justify-center border border-accent/30 text-accent font-semibold text-sm">
            {user?.name?.[0] || 'G'}
          </div>
          <div className="ml-3 flex-1 min-w-0">
            <p className={clsx(
              "text-sm font-medium truncate",
              isDark ? "text-white" : "text-gray-900"
            )}>
              {user?.name || 'Guilherme'}
            </p>
          </div>
          
          {/* Theme Toggle */}
          <button
            onClick={toggleTheme}
            className={clsx(
              "p-1.5 rounded-md transition-colors",
              isDark 
                ? "text-gray-400 hover:text-yellow-400 hover:bg-white/5" 
                : "text-gray-400 hover:text-blue-600 hover:bg-gray-100"
            )}
            title={isDark ? 'Modo Claro' : 'Modo Escuro'}
          >
            {isDark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
          </button>

          {/* Settings */}
          <button 
            onClick={() => navigate('/settings')}
            className={clsx(
              "p-1.5 rounded-md transition-colors",
              location.pathname === '/settings' ? "text-accent" : "",
              isDark 
                ? "text-gray-400 hover:text-white hover:bg-white/5" 
                : "text-gray-400 hover:text-gray-900 hover:bg-gray-100"
            )}
            title="Configurações"
          >
            <Settings className="h-4 w-4" />
          </button>

          {/* Logout */}
          <button 
            onClick={logout}
            className={clsx(
              "p-1.5 rounded-md transition-colors",
              isDark 
                ? "text-gray-400 hover:text-white hover:bg-white/5" 
                : "text-gray-400 hover:text-gray-900 hover:bg-gray-100"
            )}
            title="Sair"
          >
            <LogOut className="h-4 w-4" />
          </button>
        </div>
      </div>
    </div>
  );
};
