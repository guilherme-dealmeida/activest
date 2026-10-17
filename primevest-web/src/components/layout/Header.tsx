import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Bell, ChevronDown, LogOut, Moon, Plug, Settings, Sun } from 'lucide-react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';
import { useTheme } from '@/context/ThemeContext';
import { useAuth } from '@/context/AuthContext';
import { UserAvatar } from '@/components/ui/UserAvatar';
import { integrationsApi } from '@/api/integrations';
import { notificationsApi } from '@/api/notifications';
import { needsAttention } from '@/utils/integrations';

const routeNames: Record<string, string> = {
  '/dashboard': 'Dashboard',
  '/rentabilidade': 'Rentabilidade',
  '/portfolio': 'Carteira',
  '/allocation': 'Alocação',
  '/strategy': 'Estratégia de Investimentos',
  '/rebalance': 'Rebalanceamento',
  '/transactions': 'Transações',
  '/ceiling-prices': 'Preço Teto',
  '/emissoras': 'Emissoras',
  '/indices': 'Índices',
  '/dividends': 'Dividendos e Proventos',
  '/goals': 'Minhas Metas',
  '/integrations': 'Integrações',
  '/settings': 'Configurações',
  '/notifications': 'Notificações',
};

/** Marcador amarelo de contagem (Notificações e Integrações), sobre o canto do ícone. */
const CountBadge = ({ count }: { count: number }) => (
  <span
    className="absolute -top-1 -right-1.5 min-w-[18px] h-[18px] px-1 rounded-full bg-yellow-400/20 text-yellow-500 text-[10px] font-semibold flex items-center justify-center"
    aria-hidden
  >
    {count > 99 ? '99+' : count}
  </span>
);

/**
 * Cabeçalho: título da tela à esquerda e, à direita (17/10/2026, antes no rodapé do menu lateral), Tema,
 * Notificações, Integrações, Configurações e o usuário (foto + nome), que abre o menu com "Sair".
 */
export const Header = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { isDark, toggleTheme } = useTheme();
  const { user, logout } = useAuth();
  const currentPath = Object.keys(routeNames).find(path => location.pathname.startsWith(path));
  const title = currentPath ? routeNames[currentPath] : '';

  // Marcador em Integrações: pré-visualização esperando decisão, divergência de saldo ou chave com problema.
  const { data: connections } = useQuery({
    queryKey: ['integrations'],
    queryFn: integrationsApi.list,
    refetchInterval: 60_000,
  });
  const attention = (connections ?? []).filter(needsAttention).length;
  const integrationsTitle = attention === 0
    ? 'Integrações'
    : `Integrações · ${attention} integraç${attention === 1 ? 'ão pede' : 'ões pedem'} atenção`;

  // Sino das notificações (docs/planos/PLANO_NOTIFICACOES.md, 5.1): não lidas fora de Arquivadas.
  const { data: summary } = useQuery({
    queryKey: ['notifications', 'summary'],
    queryFn: notificationsApi.summary,
    refetchInterval: 60_000,
  });
  const unread = summary?.unread ?? 0;
  const unreadTitle = unread === 0
    ? 'Notificações'
    : `Notificações · ${unread} não lida${unread === 1 ? '' : 's'}`;

  // Menu do usuário: fecha ao clicar fora, com Esc e ao trocar de tela.
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!menuOpen) return;
    const onClick = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) setMenuOpen(false);
    };
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setMenuOpen(false); };
    document.addEventListener('mousedown', onClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onClick);
      document.removeEventListener('keydown', onKey);
    };
  }, [menuOpen]);
  useEffect(() => { setMenuOpen(false); }, [location.pathname]);

  const userName = user?.name ?? '';

  const iconButton = clsx(
    'relative p-2 rounded-md transition-colors',
    isDark
      ? 'text-gray-400 hover:text-white hover:bg-white/5'
      : 'text-gray-500 hover:text-gray-900 hover:bg-gray-100'
  );
  const isAt = (path: string) => location.pathname.startsWith(path);

  return (
    <header className={clsx(
      "h-16 border-b flex items-center justify-between gap-4 px-6 transition-colors duration-300",
      isDark
        ? "bg-[#111111] border-white/10"
        : "bg-white border-gray-200"
    )}>
      <h2 className={clsx(
        "text-xl font-semibold truncate",
        isDark ? "text-white" : "text-gray-900"
      )}>{title}</h2>

      <div className="flex items-center gap-1 shrink-0">
        {/* Tema */}
        <button
          type="button"
          onClick={toggleTheme}
          className={clsx(
            'p-2 rounded-md transition-colors',
            isDark
              ? 'text-gray-400 hover:text-yellow-400 hover:bg-white/5'
              : 'text-gray-500 hover:text-blue-600 hover:bg-gray-100'
          )}
          title={isDark ? 'Modo Claro' : 'Modo Escuro'}
          aria-label={isDark ? 'Modo Claro' : 'Modo Escuro'}
        >
          {isDark ? <Sun className="h-5 w-5" /> : <Moon className="h-5 w-5" />}
        </button>

        {/* Notificações */}
        <button
          type="button"
          onClick={() => navigate('/notifications')}
          className={twMerge(iconButton, isAt('/notifications') && 'text-accent')}
          title={unreadTitle}
          aria-label={unreadTitle}
        >
          <Bell className="h-5 w-5" />
          {unread > 0 && <CountBadge count={unread} />}
        </button>

        {/* Integrações */}
        <button
          type="button"
          onClick={() => navigate('/integrations')}
          className={twMerge(iconButton, isAt('/integrations') && 'text-accent')}
          title={integrationsTitle}
          aria-label={integrationsTitle}
        >
          <Plug className="h-5 w-5" />
          {attention > 0 && <CountBadge count={attention} />}
        </button>

        {/* Configurações */}
        <button
          type="button"
          onClick={() => navigate('/settings')}
          className={twMerge(iconButton, isAt('/settings') && 'text-accent')}
          title="Configurações"
          aria-label="Configurações"
        >
          <Settings className="h-5 w-5" />
        </button>

        {/* Usuário: foto + nome, com o menu de Sair */}
        <div className="relative ml-2" ref={menuRef}>
          <button
            type="button"
            onClick={() => setMenuOpen(o => !o)}
            aria-haspopup="menu"
            aria-expanded={menuOpen}
            className={clsx(
              'flex items-center gap-2 pl-1 pr-2 py-1 rounded-md transition-colors',
              isDark ? 'hover:bg-white/5' : 'hover:bg-gray-100'
            )}
          >
            <UserAvatar user={user} className="h-8 w-8" textClassName="text-sm" />
            <span
              className={clsx(
                'hidden lg:block max-w-[180px] text-sm font-medium truncate',
                isDark ? 'text-white' : 'text-gray-900'
              )}
              title={userName}
            >
              {userName}
            </span>
            <ChevronDown className={clsx('h-4 w-4 transition-transform', menuOpen && 'rotate-180', isDark ? 'text-gray-400' : 'text-gray-500')} />
          </button>

          {menuOpen && (
            <div
              role="menu"
              className={clsx(
                'absolute right-0 mt-2 w-48 rounded-md border shadow-lg py-1 z-50',
                isDark ? 'bg-[#111111] border-white/10' : 'bg-white border-gray-200'
              )}
            >
              <button
                type="button"
                role="menuitem"
                onClick={logout}
                className={clsx(
                  'w-full flex items-center gap-2 px-3 py-2 text-sm transition-colors',
                  isDark ? 'text-gray-300 hover:bg-white/5 hover:text-white' : 'text-gray-700 hover:bg-gray-100'
                )}
              >
                <LogOut className="h-4 w-4" />
                Sair
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
};
