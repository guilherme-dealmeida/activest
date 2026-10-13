import { useEffect, useState } from 'react';
import { NavLink, useLocation, useNavigate } from 'react-router-dom';
import {
  LayoutDashboard,
  Briefcase,
  PieChart,
  Target,
  Scale,
  ArrowLeftRight,
  TrendingDown,
  DollarSign,
  Flag,
  Plug,
  Building2,
  LineChart,
  LogOut,
  Settings,
  Sun,
  Moon,
  PanelLeftClose,
  PanelLeftOpen,
} from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '@/context/AuthContext';
import { UserAvatar } from '@/components/ui/UserAvatar';
import { integrationsApi } from '@/api/integrations';
import { needsAttention } from '@/utils/integrations';
import { useTheme } from '@/context/ThemeContext';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

// Ordem definida em 23/09/2026: o fluxo de uso (ver, lançar, analisar,
// planejar) e depois as consultas de apoio. Rebalanceamento logo depois de
// Estratégia (11/10/2026), quando deixou de ser aba dela.
const navItems = [
  { name: 'Dashboard', path: '/dashboard', icon: LayoutDashboard },
  { name: 'Carteira', path: '/portfolio', icon: Briefcase },
  { name: 'Transações', path: '/transactions', icon: ArrowLeftRight },
  { name: 'Alocação', path: '/allocation', icon: PieChart },
  { name: 'Estratégia', path: '/strategy', icon: Target },
  { name: 'Rebalanceamento', path: '/rebalance', icon: Scale },
  { name: 'Dividendos', path: '/dividends', icon: DollarSign },
  { name: 'Preço Teto', path: '/ceiling-prices', icon: TrendingDown },
  { name: 'Emissoras', path: '/emissoras', icon: Building2 },
  { name: 'Índices', path: '/indices', icon: LineChart },
  { name: 'Metas', path: '/goals', icon: Flag },
  { name: 'Integrações', path: '/integrations', icon: Plug },
];

/**
 * Menu recolhido ou expandido, lembrado entre recargas como o tema.
 *
 * O localStorage pode falhar (aba anônima, armazenamento bloqueado): aí o menu
 * só não lembra a escolha, e abre expandido.
 */
const COLLAPSED_KEY = 'primevest-sidebar-collapsed';

const readCollapsed = () => {
  try {
    return localStorage.getItem(COLLAPSED_KEY) === 'true';
  } catch {
    return false;
  }
};

export const Sidebar = () => {
  const { user, logout } = useAuth();
  const { toggleTheme, isDark } = useTheme();
  const location = useLocation();
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(readCollapsed);

  useEffect(() => {
    try {
      localStorage.setItem(COLLAPSED_KEY, String(collapsed));
    } catch {
      // Sem armazenamento a escolha vale só para esta visita.
    }
  }, [collapsed]);

  // Marcador em Integrações: pré-visualização esperando decisão, divergência
  // de saldo ou chave com problema. Consulta leve, a cada minuto.
  const { data: connections } = useQuery({
    queryKey: ['integrations'],
    queryFn: integrationsApi.list,
    refetchInterval: 60_000,
  });
  const attention = (connections ?? []).filter(needsAttention).length;

  const userName = user?.name ?? '';

  const footerButton = clsx(
    'p-1.5 rounded-md transition-colors',
    isDark
      ? 'text-gray-400 hover:text-white hover:bg-white/5'
      : 'text-gray-400 hover:text-gray-900 hover:bg-gray-100'
  );

  return (
    <div className={clsx(
      'relative flex flex-col shrink-0 h-full border-r transition-[width,background-color,border-color] duration-300',
      collapsed ? 'w-[72px]' : 'w-64',
      isDark
        ? 'bg-[#111111] border-white/10'
        : 'bg-white border-gray-200'
    )}>
      {/* Recolher/expandir: sobre a borda direita, na altura do logo. Fica no
          mesmo lugar nos dois estados, sem disputar os 72px com o "PV". */}
      <button
        type="button"
        onClick={() => setCollapsed(c => !c)}
        aria-expanded={!collapsed}
        aria-label={collapsed ? 'Expandir menu' : 'Recolher menu'}
        title={collapsed ? 'Expandir menu' : 'Recolher menu'}
        className={clsx(
          'absolute -right-3 top-[34px] z-20 h-6 w-6 rounded-full border flex items-center justify-center shadow-sm transition-colors',
          isDark
            ? 'bg-[#111111] border-white/15 text-gray-400 hover:text-accent hover:border-accent/50'
            : 'bg-white border-gray-300 text-gray-500 hover:text-accent hover:border-accent/50'
        )}
      >
        {collapsed ? <PanelLeftOpen className="h-3.5 w-3.5" /> : <PanelLeftClose className="h-3.5 w-3.5" />}
      </button>

      {/* Logo: altura fixa, para os itens não pularem quando o slogan some. */}
      <div className={clsx(
        'h-[92px] flex flex-col items-center justify-center px-4 border-b overflow-hidden transition-colors duration-300',
        isDark ? 'border-white/10' : 'border-gray-200'
      )}>
        {collapsed ? (
          <h1
            className={clsx('text-2xl font-bold tracking-tight', isDark ? 'text-white' : 'text-gray-900')}
            title="PrimeVest"
          >
            P<span className="text-accent">V</span>
          </h1>
        ) : (
          <>
            <h1 className={clsx('text-2xl font-bold tracking-tight whitespace-nowrap', isDark ? 'text-white' : 'text-gray-900')}>
              Prime<span className="text-accent">Vest</span>
            </h1>
            <p className={clsx('text-[10px] uppercase tracking-[0.2em] mt-1 whitespace-nowrap', isDark ? 'text-gray-500' : 'text-gray-400')}>
              Disciplina leva à liberdade
            </p>
          </>
        )}
      </div>

      <nav className={clsx('flex-1 py-6 space-y-1 overflow-y-auto overflow-x-hidden', collapsed ? 'px-3' : 'px-4')}>
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = location.pathname.startsWith(item.path);
          const badge = item.path === '/integrations' && attention > 0;
          const badgeTitle = `${attention} integraç${attention === 1 ? 'ão pede' : 'ões pedem'} atenção`;

          return (
            <NavLink
              key={item.path}
              to={item.path}
              // Recolhido o nome não aparece: vira dica no mouse e rótulo para
              // leitor de tela.
              title={collapsed ? (badge ? `${item.name} · ${badgeTitle}` : item.name) : undefined}
              aria-label={collapsed ? item.name : undefined}
              className={twMerge(
                clsx(
                  'group flex items-center py-2.5 text-sm font-medium rounded-md transition-colors whitespace-nowrap overflow-hidden',
                  collapsed ? 'justify-center px-0' : 'px-3',
                  isActive
                    ? isDark
                      ? 'bg-white/10 text-accent'
                      : 'bg-accent/10 text-accent'
                    : isDark
                      ? 'text-gray-300 hover:bg-white/5 hover:text-white'
                      : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900'
                )
              )}
            >
              <span className={clsx('relative flex-shrink-0', !collapsed && 'mr-3')}>
              <Icon
                className={clsx(
                  'flex-shrink-0 h-5 w-5',
                  isActive
                    ? 'text-accent'
                    : isDark
                      ? 'text-gray-400 group-hover:text-gray-300'
                      : 'text-gray-400 group-hover:text-gray-600'
                )}
              />
              {badge && collapsed && (
                <span
                  className={clsx('absolute -top-1 -right-1 h-2.5 w-2.5 rounded-full bg-yellow-400 ring-2', isDark ? 'ring-[#111111]' : 'ring-white')}
                  aria-hidden
                />
              )}
              </span>
              {!collapsed && item.name}
              {badge && !collapsed && (
                <span
                  className="ml-auto min-w-[20px] h-5 px-1.5 rounded-full bg-yellow-400/20 text-yellow-500 text-xs font-semibold flex items-center justify-center"
                  title={badgeTitle}
                  aria-label={badgeTitle}
                >
                  {attention}
                </span>
              )}
            </NavLink>
          );
        })}
      </nav>

      <div className={clsx(
        'p-4 border-t overflow-hidden transition-colors duration-300',
        isDark ? 'border-white/10' : 'border-gray-200'
      )}>
        <div className={clsx('flex items-center', collapsed && 'flex-col gap-2')}>
          {/* A foto do usuário (enviada, do Google ou a inicial) — a mesma de Configurações. */}
          <UserAvatar user={user} className="h-8 w-8" textClassName="text-sm" title={collapsed ? userName : undefined} />
          {!collapsed && (
            <div className="ml-3 flex-1 min-w-0">
              <p className={clsx(
                'text-sm font-medium truncate',
                isDark ? 'text-white' : 'text-gray-900'
              )}>
                {userName}
              </p>
            </div>
          )}

          {/* Theme Toggle */}
          <button
            onClick={toggleTheme}
            className={clsx(
              'p-1.5 rounded-md transition-colors',
              isDark
                ? 'text-gray-400 hover:text-yellow-400 hover:bg-white/5'
                : 'text-gray-400 hover:text-blue-600 hover:bg-gray-100'
            )}
            title={isDark ? 'Modo Claro' : 'Modo Escuro'}
            aria-label={isDark ? 'Modo Claro' : 'Modo Escuro'}
          >
            {isDark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
          </button>

          {/* Settings */}
          <button
            onClick={() => navigate('/settings')}
            className={twMerge(footerButton, location.pathname === '/settings' && 'text-accent')}
            title="Configurações"
            aria-label="Configurações"
          >
            <Settings className="h-4 w-4" />
          </button>

          {/* Logout */}
          <button
            onClick={logout}
            className={footerButton}
            title="Sair"
            aria-label="Sair"
          >
            <LogOut className="h-4 w-4" />
          </button>
        </div>
      </div>
    </div>
  );
};
