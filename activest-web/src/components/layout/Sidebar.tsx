import { useEffect, useState } from 'react';
import { Link, NavLink, useLocation } from 'react-router-dom';
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
import { useAuth } from '@/context/AuthContext';
import { useTheme } from '@/context/ThemeContext';
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
 * Grupos do menu. Nenhum item muda de lugar: os rótulos só separam a lista em
 * blocos, na ordem que já existia.
 */
const GROUPS = [
  { label: 'Posição', from: 0 },
  { label: 'Análise', from: 3 },
  { label: 'Planejamento', from: 8 },
  { label: 'Mercado', from: 10 },
  { label: 'Administração', from: 12 },
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

interface SidebarProps {
  /** Gaveta aberta (tela estreita). */
  open?: boolean;
  /** Fecha a gaveta ao escolher um item. */
  onClose?: () => void;
}

export const Sidebar = ({ open = false, onClose }: SidebarProps) => {
  const { isDark } = useTheme();
  // O tema só decide a variante do logo (o arquivo tem a palavra em duas cores).
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

  const items = navItems.filter(item => !item.adminOnly || user?.admin);

  return (
    <>
      {/* Véu da gaveta (só na tela estreita). */}
      {open && (
        <div
          className="fixed inset-0 z-40 bg-black/60 lg:hidden"
          onClick={onClose}
          aria-hidden
        />
      )}

      <div
        className={clsx(
          // Abaixo de `lg` a gaveta: fora do fluxo, sobre o conteúdo, e some
          // com um toque no véu. De `lg` em diante é a coluna fixa de sempre.
          // `visibility` entra na transição para a gaveta fechada sair do tab
          // (teclado) sem cortar a animação de saída.
          'fixed inset-y-0 left-0 z-50 flex flex-col h-full w-64 bg-surface border-r border-line',
          'transition-[transform,visibility] duration-200 lg:static lg:visible lg:z-auto lg:translate-x-0',
          open ? 'translate-x-0 visible' : '-translate-x-full invisible',
          collapsed ? 'lg:w-[72px]' : 'lg:w-64'
        )}
      >
        {/* Recolher/expandir: sobre a borda direita, na altura do logo. Fica no
            mesmo lugar nos dois estados, sem disputar os 72px com o "PV". */}
        <button
          type="button"
          onClick={() => setCollapsed(c => !c)}
          aria-expanded={!collapsed}
          aria-label={collapsed ? 'Expandir menu' : 'Recolher menu'}
          title={collapsed ? 'Expandir menu' : 'Recolher menu'}
          className={clsx(
            'hidden lg:flex absolute -right-3 top-[24px] z-20 h-6 w-6 rounded-full border border-line-strong bg-surface items-center justify-center shadow-sm transition-colors',
            'text-content-3 hover:text-accent-text hover:border-accent/50'
          )}
        >
          {collapsed ? <PanelLeftOpen className="h-3.5 w-3.5" /> : <PanelLeftClose className="h-3.5 w-3.5" />}
        </button>

        {/* Logo: altura fixa, para os itens não pularem quando o slogan some. */}
        <div className="h-[72px] flex items-center justify-center px-4 border-b border-line overflow-hidden">
          {/* Clicar no logo leva ao Dashboard (página inicial). */}
          <Link to="/dashboard" title="Ir para o Dashboard" className="block w-full">
            <img
              src={isDark ? '/images/gv2.png' : '/images/v1.png'}
              alt="Activest — Disciplina leva à liberdade"
              className={clsx('mx-auto w-full max-w-[320px] h-auto object-contain', collapsed ? 'hidden lg:block' : 'hidden')}
            />
            <img
              src={isDark ? '/images/activest2.png' : '/images/activest1.png'}
              alt="Activest — Disciplina leva à liberdade"
              className={clsx('mx-auto w-full max-w-[320px] h-auto object-contain', collapsed ? 'lg:hidden' : '')}
            />
          </Link>
        </div>

        <nav className="flex-1 py-4 overflow-y-auto overflow-x-hidden px-3">
          {items.map((item, idx) => {
            const Icon = item.icon;
            const isActive = location.pathname.startsWith(item.path);
            const group = GROUPS.find(g => g.from === idx);

            return (
              <div key={item.path}>
                {group && (
                  <p className={clsx(
                    'px-3 pt-4 pb-1.5 text-[0.625rem] font-semibold uppercase tracking-[0.14em] text-content-4',
                    collapsed && 'lg:hidden'
                  )}>
                    {group.label}
                  </p>
                )}
                {group && collapsed && <div className="hidden lg:block mx-2 my-3 border-t border-line-soft" aria-hidden />}
                <NavLink
                  to={item.path}
                  onClick={onClose}
                  // Recolhido o nome não aparece: vira dica no mouse e rótulo para
                  // leitor de tela.
                  title={collapsed ? item.name : undefined}
                  aria-label={collapsed ? item.name : undefined}
                  aria-current={isActive ? 'page' : undefined}
                  className={twMerge(
                    clsx(
                      'group relative flex items-center h-9 text-sm font-medium rounded-control transition-colors whitespace-nowrap overflow-hidden',
                      collapsed ? 'lg:justify-center lg:px-0 px-3' : 'px-3',
                      isActive
                        ? 'bg-selected text-accent-text'
                        : 'text-content-2 hover:bg-overlay hover:text-content'
                    )
                  )}
                >
                  {/* Trilho do item ativo, encostado na borda do menu. */}
                  {isActive && <span className="absolute left-0 -ml-3 h-5 w-0.5 rounded-full bg-accent" aria-hidden />}
                  <span className={clsx('relative flex-shrink-0', collapsed ? 'mr-3 lg:mr-0' : 'mr-3')}>
                    <Icon
                      className={clsx(
                        'flex-shrink-0 h-[18px] w-[18px] transition-colors',
                        isActive ? 'text-accent-text' : 'text-content-4 group-hover:text-content-2'
                      )}
                    />
                  </span>
                  <span className={clsx(collapsed && 'lg:hidden')}>{item.name}</span>
                </NavLink>
              </div>
            );
          })}
        </nav>
      </div>
    </>
  );
};
