import { useEffect, useState } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import {
  LayoutDashboard,
  TrendingUp,
  Wallet,
  PieChart,
  Target,
  Scale,
  ArrowLeftRight,
  TrendingDown,
  HandCoins,
  Flag,
  CandlestickChart,
  LineChart,
  Clock,
  Coins,
  PanelLeftClose,
  PanelLeftOpen,
} from 'lucide-react';
import { useTheme } from '@/context/ThemeContext';
import { useAuth } from '@/context/AuthContext';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

// Ordem definida em 17/10/2026 (escopo "Mudanças de tela"). Integrações saiu do menu e foi para o cabeçalho, junto
// de Tema, Notificações, Configurações e do usuário (antes no rodapé deste menu).
const navItems = [
  { name: 'Dashboard', path: '/dashboard', icon: LayoutDashboard },
  { name: 'Carteira', path: '/portfolio', icon: Wallet },
  { name: 'Transações', path: '/transactions', icon: ArrowLeftRight },
  { name: 'Dividendos', path: '/dividends', icon: HandCoins },
  { name: 'Rentabilidade', path: '/performance', icon: TrendingUp },
  { name: 'Alocação', path: '/allocation', icon: PieChart },
  { name: 'Estratégia', path: '/strategy', icon: Target },
  { name: 'Rebalanceamento', path: '/rebalance', icon: Scale },
  { name: 'Preço Teto', path: '/ceiling-prices', icon: TrendingDown },
  { name: 'Metas', path: '/goals', icon: Flag },
  { name: 'Ativos', path: '/assets', icon: CandlestickChart },
  { name: 'Índices', path: '/indices', icon: LineChart },
  // Tarefas (docs/planos/PLANO_TAREFAS.md, D14): só administrador, último item, ícone de relógio
  { name: 'Tarefas', path: '/tasks', icon: Clock, adminOnly: true },
  // Moedas (docs/planos/PLANO_CADASTRO_MOEDAS.md, D13): só administrador, junto de Tarefas
  { name: 'Moedas', path: '/currencies', icon: Coins, adminOnly: true },
];

/**
 * Menu recolhido ou expandido, lembrado entre recargas como o tema.
 *
 * O localStorage pode falhar (aba anônima, armazenamento bloqueado): aí o menu
 * só não lembra a escolha, e abre expandido.
 */
const COLLAPSED_KEY = 'activest-sidebar-collapsed';

const readCollapsed = () => {
  try {
    return localStorage.getItem(COLLAPSED_KEY) === 'true';
  } catch {
    return false;
  }
};

export const Sidebar = () => {
  const { isDark } = useTheme();
  const { user } = useAuth();
  const location = useLocation();
  const [collapsed, setCollapsed] = useState(readCollapsed);

  useEffect(() => {
    try {
      localStorage.setItem(COLLAPSED_KEY, String(collapsed));
    } catch {
      // Sem armazenamento a escolha vale só para esta visita.
    }
  }, [collapsed]);

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
          <img src={isDark ? '/images/gv2.png' : '/images/v1.png'} alt="Activest — Disciplina leva à liberdade" className="mx-auto w-full max-w-[320px] h-auto object-contain" />
        ) : (
          <>
             <img src={isDark ? '/images/activest2.png' : '/images/activest1.png'} alt="Activest — Disciplina leva à liberdade" className="mx-auto w-full max-w-[320px] h-auto object-contain" />
          </>
        )}
      </div>

      <nav className={clsx('flex-1 py-6 space-y-1 overflow-y-auto overflow-x-hidden', collapsed ? 'px-3' : 'px-4')}>
        {navItems.filter(item => !item.adminOnly || user?.admin).map((item) => {
          const Icon = item.icon;
          const isActive = location.pathname.startsWith(item.path);

          return (
            <NavLink
              key={item.path}
              to={item.path}
              // Recolhido o nome não aparece: vira dica no mouse e rótulo para
              // leitor de tela.
              title={collapsed ? item.name : undefined}
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
              </span>
              {!collapsed && item.name}
            </NavLink>
          );
        })}
      </nav>
    </div>
  );
};
