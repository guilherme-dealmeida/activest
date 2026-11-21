import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { AArrowDown, AArrowUp, Bell, ChevronDown, LogOut, Menu, Moon, Plug, Settings, Sun } from 'lucide-react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';
import { useFontSize } from '@/context/FontSizeContext';
import { useTheme } from '@/context/ThemeContext';
import { useAuth } from '@/context/AuthContext';
import { UserAvatar } from '@/components/ui/UserAvatar';
import { integrationsApi } from '@/api/integrations';
import { notificationsApi } from '@/api/notifications';
import { needsAttention } from '@/utils/integrations';

/** Título da tela e o bloco do menu a que ela pertence (o mesmo do menu lateral). */
const routeNames: Record<string, { title: string; section: string }> = {
  '/dashboard': { title: 'Dashboard', section: 'Posição' },
  '/portfolio': { title: 'Carteira', section: 'Posição' },
  '/transactions': { title: 'Transações', section: 'Posição' },
  '/dividends': { title: 'Dividendos e Proventos', section: 'Análise' },
  '/performance': { title: 'Rentabilidade', section: 'Análise' },
  '/allocation': { title: 'Alocação', section: 'Análise' },
  '/strategy': { title: 'Estratégia de Investimentos', section: 'Planejamento' },
  '/rebalance': { title: 'Rebalanceamento', section: 'Planejamento' },
  '/ceiling-prices': { title: 'Preço Teto', section: 'Planejamento' },
  '/goals': { title: 'Minhas Metas', section: 'Planejamento' },
  '/assets': { title: 'Ativos', section: 'Mercado' },
  '/indices': { title: 'Índices', section: 'Mercado' },
  '/tasks': { title: 'Tarefas', section: 'Administração' },
  '/currencies': { title: 'Moedas', section: 'Administração' },
  '/integrations': { title: 'Integrações', section: 'Configurações' },
  '/settings': { title: 'Configurações', section: 'Configurações' },
  '/notifications': { title: 'Notificações', section: 'Configurações' },
};

/** Marcador de contagem (Notificações e Integrações), sobre o canto do ícone. */
const CountBadge = ({ count }: { count: number }) => (
  <span
    className="absolute -top-0.5 -right-0.5 min-w-[16px] h-4 px-1 rounded-full bg-accent/20 text-accent-text text-[0.5625rem] font-semibold flex items-center justify-center tabular-nums"
    aria-hidden
  >
    {count > 99 ? '99+' : count}
  </span>
);

/**
 * Cabeçalho: o bloco da tela à esquerda e, à direita (17/10/2026, antes no rodapé do menu lateral), Tamanho da tela
 * (A−/A+, PLANO_CUSTOMIZAR_TAMANHOS), Tema,
 * Notificações, Integrações, Configurações e o usuário (foto + nome), que abre o menu com "Sair".
 */
export const Header = ({ onToggleMenu }: { onToggleMenu?: () => void }) => {
  const location = useLocation();
  const navigate = useNavigate();
  const { isDark, toggleTheme } = useTheme();
  const { isSmall, toggleFontSize } = useFontSize();
  const { user, logout } = useAuth();
  const currentPath = Object.keys(routeNames).find(path => location.pathname.startsWith(path));
  const route = currentPath ? routeNames[currentPath] : null;

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
    'relative h-9 w-9 rounded-control flex items-center justify-center transition-colors',
    'text-content-3 hover:text-content hover:bg-overlay'
  );
  const isAt = (path: string) => location.pathname.startsWith(path);

  return (
    <header className="h-14 shrink-0 bg-surface border-b border-line flex items-center justify-between gap-3 px-4 sm:px-6">
      <div className="flex items-center gap-2 min-w-0">
        {/* Menu em tela estreita: lá o menu lateral é gaveta (ver Sidebar). */}
        {onToggleMenu && (
          <button
            type="button"
            onClick={onToggleMenu}
            aria-label="Abrir menu"
            title="Abrir menu"
            className="lg:hidden relative h-9 w-9 rounded-control flex items-center justify-center transition-colors text-content-3 hover:text-content hover:bg-overlay"
          >
            <Menu className="h-[18px] w-[18px]" />
          </button>
        )}
        <div className="min-w-0">
          {route && (
            <p className="text-[0.625rem] font-semibold uppercase tracking-[0.16em] text-content-4 leading-none">
              {route.section}
            </p>
          )}
          <h2 className={clsx(
            'truncate leading-tight mt-0.5 text-[0.9375rem] font-semibold tracking-[-0.01em] text-content',
            !route && 'mt-0'
          )}>{route?.title ?? ''}</h2>
        </div>
      </div>

      <div className="flex items-center gap-0.5 shrink-0">
        {/* Tamanho da tela (PLANO_CUSTOMIZAR_TAMANHOS, D2): mostra a ação disponível, como o sol/lua do tema */}
        <button
          type="button"
          onClick={toggleFontSize}
          className={clsx(iconButton, 'hover:text-accent-text', 'hidden sm:flex')}
          title={isSmall ? 'Tamanho grande' : 'Tamanho pequeno'}
          aria-label={isSmall ? 'Tamanho grande' : 'Tamanho pequeno'}
        >
          {isSmall ? <AArrowUp className="h-[18px] w-[18px]" /> : <AArrowDown className="h-[18px] w-[18px]" />}
        </button>

        {/* Tema */}
        <button
          type="button"
          onClick={toggleTheme}
          className={clsx(iconButton, 'hover:text-accent-text')}
          title={isDark ? 'Modo Claro' : 'Modo Escuro'}
          aria-label={isDark ? 'Modo Claro' : 'Modo Escuro'}
        >
          {isDark ? <Sun className="h-[18px] w-[18px]" /> : <Moon className="h-[18px] w-[18px]" />}
        </button>

        <span className="mx-2 h-5 w-px bg-line" aria-hidden />

        {/* Notificações */}
        <button
          type="button"
          onClick={() => navigate('/notifications')}
          className={twMerge(iconButton, isAt('/notifications') && 'text-accent-text')}
          title={unreadTitle}
          aria-label={unreadTitle}
        >
          <Bell className="h-[18px] w-[18px]" />
          {unread > 0 && <CountBadge count={unread} />}
        </button>

        {/* Integrações */}
        <button
          type="button"
          onClick={() => navigate('/integrations')}
          className={twMerge(iconButton, isAt('/integrations') && 'text-accent-text')}
          title={integrationsTitle}
          aria-label={integrationsTitle}
        >
          <Plug className="h-[18px] w-[18px]" />
          {attention > 0 && <CountBadge count={attention} />}
        </button>

        {/* Configurações */}
        <button
          type="button"
          onClick={() => navigate('/settings')}
          className={twMerge(iconButton, isAt('/settings') && 'text-accent-text')}
          title="Configurações"
          aria-label="Configurações"
        >
          <Settings className="h-[18px] w-[18px]" />
        </button>

        {/* Usuário: foto + nome, com o menu de Sair */}
        <div className="relative ml-2" ref={menuRef}>
          <button
            type="button"
            onClick={() => setMenuOpen(o => !o)}
            aria-haspopup="menu"
            aria-expanded={menuOpen}
            className="flex items-center gap-2 pl-1 pr-2 py-1 rounded-control transition-colors hover:bg-overlay"
          >
            <UserAvatar user={user} className="h-8 w-8" textClassName="text-sm" />
            <span className="hidden lg:block max-w-[180px] text-sm font-medium truncate text-content" title={userName}>
              {userName}
            </span>
            <ChevronDown className={clsx('h-4 w-4 transition-transform', menuOpen && 'rotate-180', 'text-content-3')} />
          </button>

          {menuOpen && (
            <div
              role="menu"
              className="absolute right-0 mt-2 w-48 rounded-control border border-line bg-surface py-1 z-50 shadow-xl shadow-black/20"
            >
              <button
                type="button"
                role="menuitem"
                onClick={logout}
                className="w-full flex items-center gap-2 px-3 py-2 text-sm transition-colors text-content-2 hover:bg-overlay hover:text-content"
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
