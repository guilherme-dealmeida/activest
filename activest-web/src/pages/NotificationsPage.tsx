import { ReactNode, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Archive, ArchiveRestore, ArrowLeft, Bell, Building2, CheckCheck, ChevronLeft, ChevronRight, CircleSlash, DollarSign,
  Inbox, Landmark, Mail, MailOpen, Plug, RotateCcw, Shield, Shuffle, Trash2, TrendingDown, Wallet,
} from 'lucide-react';
import { clsx } from 'clsx';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { InformCostModal } from '@/components/notifications/InformCostModal';
import { notificationsApi, NotificationFilters } from '@/api/notifications';
import { useAuth } from '@/context/AuthContext';
import { useToast } from '@/context/ToastContext';
import { apiMessage } from '@/utils/apiError';
import { formatDateTime } from '@/utils/integrations';
import {
  NotificationAction, NotificationCategory, NotificationItem, NotificationStateAction, NotificationStatus,
} from '@/types';

const CATEGORY: Record<NotificationCategory, { label: string; icon: typeof Bell }> = {
  PROVENTOS: { label: 'Proventos', icon: DollarSign },
  EVENTOS: { label: 'Eventos societários', icon: Shuffle },
  RENDA_FIXA: { label: 'Renda fixa', icon: Landmark },
  PRECO_TETO: { label: 'Preço Teto', icon: TrendingDown },
  INTEGRACOES: { label: 'Integrações', icon: Plug },
  CARTEIRA: { label: 'Carteira', icon: Wallet },
  SISTEMA: { label: 'Sistema', icon: Shield },
};

const STATUS: Record<NotificationStatus, { label: string; variant: 'warning' | 'default' | 'success' }> = {
  NAO_RESOLVIDA: { label: 'Não resolvida', variant: 'warning' },
  DISPENSADA: { label: 'Dispensada', variant: 'default' },
  RESOLVIDA: { label: 'Resolvida', variant: 'success' },
};

const STATUS_FILTER = [
  { value: '', label: 'Todas as situações' },
  { value: 'NAO_RESOLVIDA', label: 'Não resolvidas' },
  { value: 'DISPENSADA', label: 'Dispensadas' },
  { value: 'RESOLVIDA', label: 'Resolvidas' },
  { value: 'INFORMATIVA', label: 'Informativas' },
];

const selectClass =
  'bg-surface border border-surface-light text-gray-300 text-sm rounded-md px-2 py-1.5 focus:ring-accent focus:border-accent';

/** "há 5 min", "ontem 14:02" ou a data — o carimbo da API vem no relógio do servidor (UTC). */
const when = (value: string): string => {
  const iso = /[zZ]|[+-]\d{2}:\d{2}$/.test(value) ? value : `${value}Z`;
  const d = new Date(iso);
  const diffMin = Math.round((Date.now() - d.getTime()) / 60_000);
  if (diffMin < 1) return 'agora';
  if (diffMin < 60) return `há ${diffMin} min`;
  if (diffMin < 24 * 60) return `há ${Math.floor(diffMin / 60)} h`;
  return formatDateTime(value);
};

/**
 * Notificações (docs/planos/PLANO_NOTIFICACOES.md, 5.1): caixa inspirada em e-mail — lista à esquerda, leitura à
 * direita. Estado (lida/arquivada/excluída) e status do assunto (não resolvida/dispensada/resolvida) são
 * independentes: abrir só marca como lida; dispensar só muda o status.
 */
export const NotificationsPage = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { showToast } = useToast();

  const [filters, setFilters] = useState<NotificationFilters>({ folder: 'INBOX', page: 0 });
  const [selected, setSelected] = useState<NotificationItem | null>(null);
  const [checked, setChecked] = useState<Set<string>>(new Set());
  const [confirmDelete, setConfirmDelete] = useState<string[] | null>(null);
  const [informCost, setInformCost] = useState<{ item: NotificationItem; action: NotificationAction } | null>(null);

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['notifications', 'list', filters],
    queryFn: () => notificationsApi.list(filters),
    refetchInterval: 60_000,
  });
  const items = useMemo(() => data?.items ?? [], [data]);
  const totalPages = data ? Math.max(1, Math.ceil(data.total / data.size)) : 1;

  // A lista mudou (filtro, página, ação): a seleção em lote só vale para o que está na tela.
  useEffect(() => {
    setChecked(prev => new Set([...prev].filter(id => items.some(i => i.id === id))));
    setSelected(prev => (prev ? items.find(i => i.id === prev.id) ?? prev : prev));
  }, [items]);

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['notifications'] });

  const open = useMutation({
    mutationFn: (id: string) => notificationsApi.open(id),
    onSuccess: (item) => { setSelected(item); refresh(); },
  });

  const state = useMutation({
    mutationFn: ({ ids, action }: { ids: string[]; action: NotificationStateAction }) =>
      notificationsApi.state(ids, action),
    onSuccess: (_r, { ids, action }) => {
      if ((action === 'DELETE' || action === 'ARCHIVE' || action === 'UNARCHIVE') && selected && ids.includes(selected.id)) {
        setSelected(null);
      }
      if (action === 'UNREAD' && selected && ids.includes(selected.id)) {
        setSelected({ ...selected, read: false });
      }
      setChecked(new Set());
      refresh();
    },
    onError: (e) => showToast(apiMessage(e) ?? 'Não foi possível atualizar as mensagens.', 'error'),
  });

  const status = useMutation({
    mutationFn: ({ id, action }: { id: string; action: 'DISMISS' | 'REOPEN' }) => notificationsApi.status(id, action),
    onSuccess: (item) => { setSelected(item); refresh(); },
    onError: (e) => showToast(apiMessage(e) ?? 'Não foi possível mudar a situação.', 'error'),
  });

  const setFilter = (patch: Partial<NotificationFilters>) => {
    setFilters(f => ({ ...f, ...patch, page: patch.page ?? 0 }));
    if (patch.folder) setSelected(null);
  };

  const select = (item: NotificationItem) => {
    setSelected(item);
    if (!item.read) open.mutate(item.id);
  };

  const toggle = (id: string) => setChecked(prev => {
    const next = new Set(prev);
    if (next.has(id)) next.delete(id); else next.add(id);
    return next;
  });
  const allChecked = items.length > 0 && items.every(i => checked.has(i.id));
  const toggleAll = () => setChecked(allChecked ? new Set() : new Set(items.map(i => i.id)));

  const runAction = (item: NotificationItem, action: NotificationAction) => {
    if (action.type === 'LINK' && action.target) {
      navigate(action.target);
    } else if (action.type === 'INFORMAR_CUSTO') {
      setInformCost({ item, action });
    }
  };

  const archived = filters.folder === 'ARCHIVED';
  const bulk = [...checked];

  return (
    <div className="space-y-4">
      {/* Pastas e filtros */}
      <div className="flex flex-wrap items-center gap-3">
        <div className="inline-flex rounded-lg border border-surface-light bg-surface p-1">
          {([['INBOX', 'Entrada', Inbox], ['ARCHIVED', 'Arquivadas', Archive]] as const).map(([value, label, Icon]) => (
            <button
              key={value}
              onClick={() => setFilter({ folder: value })}
              className={clsx(
                'flex items-center gap-2 rounded-md px-3 py-1.5 text-sm font-medium transition-colors',
                filters.folder === value ? 'bg-white/10 text-accent' : 'text-gray-400 hover:text-white'
              )}
            >
              <Icon className="h-4 w-4" /> {label}
            </button>
          ))}
        </div>

        <select
          aria-label="Categoria"
          className={selectClass}
          value={filters.category ?? ''}
          onChange={e => setFilter({ category: e.target.value || undefined })}
        >
          <option value="">Todas as categorias</option>
          {Object.entries(CATEGORY)
            .filter(([k]) => k !== 'SISTEMA' || user?.admin)
            .map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
        </select>

        <select
          aria-label="Situação"
          className={selectClass}
          value={filters.status ?? ''}
          onChange={e => setFilter({ status: e.target.value || undefined })}
        >
          {STATUS_FILTER.map(s => <option key={s.value} value={s.value}>{s.label}</option>)}
        </select>

        {user?.admin && (
          <select
            aria-label="Público"
            className={selectClass}
            value={filters.audience ?? ''}
            onChange={e => setFilter({ audience: (e.target.value || undefined) as NotificationFilters['audience'] })}
          >
            <option value="">Minhas e administração</option>
            <option value="USER">Só minhas</option>
            <option value="ADMIN">Administração</option>
          </select>
        )}

        <label className="flex items-center gap-2 text-sm text-gray-400 cursor-pointer select-none">
          <input
            type="checkbox"
            className="accent-[#c9a84c]"
            checked={!!filters.unread}
            onChange={e => setFilter({ unread: e.target.checked })}
          />
          Só não lidas
        </label>

        {isFetching && !isLoading && <LoadingSpinner className="h-4 w-4 text-accent" />}
      </div>

      <div className="grid gap-4 lg:grid-cols-[minmax(320px,440px)_1fr]">
        {/* Lista */}
        <Card className={clsx('p-0 overflow-hidden', selected && 'hidden lg:block')}>
          <div className="flex items-center gap-2 border-b border-surface-light px-4 py-2.5">
            <input
              type="checkbox"
              className="accent-[#c9a84c]"
              aria-label="Selecionar todas"
              checked={allChecked}
              onChange={toggleAll}
              disabled={items.length === 0}
            />
            {bulk.length === 0 ? (
              <span className="text-xs text-gray-500">
                {data ? `${data.total} mensage${data.total === 1 ? 'm' : 'ns'}` : ''}
              </span>
            ) : (
              <div className="flex flex-wrap items-center gap-1">
                <span className="mr-1 text-xs text-gray-400">{bulk.length} selecionada{bulk.length === 1 ? '' : 's'}</span>
                <IconButton title="Marcar como lidas" onClick={() => state.mutate({ ids: bulk, action: 'READ' })}>
                  <MailOpen className="h-4 w-4" />
                </IconButton>
                <IconButton title="Marcar como não lidas" onClick={() => state.mutate({ ids: bulk, action: 'UNREAD' })}>
                  <Mail className="h-4 w-4" />
                </IconButton>
                {archived ? (
                  <IconButton title="Desarquivar" onClick={() => state.mutate({ ids: bulk, action: 'UNARCHIVE' })}>
                    <ArchiveRestore className="h-4 w-4" />
                  </IconButton>
                ) : (
                  <IconButton title="Arquivar" onClick={() => state.mutate({ ids: bulk, action: 'ARCHIVE' })}>
                    <Archive className="h-4 w-4" />
                  </IconButton>
                )}
                <IconButton title="Excluir" danger onClick={() => setConfirmDelete(bulk)}>
                  <Trash2 className="h-4 w-4" />
                </IconButton>
              </div>
            )}
          </div>

          {isLoading ? (
            <div className="flex justify-center py-16"><LoadingSpinner className="h-6 w-6 text-accent" /></div>
          ) : items.length === 0 ? (
            <div className="flex flex-col items-center gap-2 px-6 py-16 text-center text-sm text-gray-500">
              <CheckCheck className="h-8 w-8 text-gray-600" />
              {archived ? 'Nenhuma mensagem arquivada.' : 'Nenhuma mensagem por aqui.'}
            </div>
          ) : (
            <ul className="max-h-[calc(100vh-15rem)] divide-y divide-surface-light overflow-y-auto">
              {items.map(item => {
                const Icon = CATEGORY[item.category]?.icon ?? Bell;
                const active = selected?.id === item.id;
                return (
                  <li
                    key={item.id}
                    className={clsx(
                      'flex cursor-pointer gap-3 px-4 py-3 transition-colors',
                      active ? 'bg-white/10' : 'hover:bg-white/5'
                    )}
                    onClick={() => select(item)}
                  >
                    <input
                      type="checkbox"
                      className="mt-1 accent-[#c9a84c]"
                      aria-label={`Selecionar ${item.title}`}
                      checked={checked.has(item.id)}
                      onClick={e => e.stopPropagation()}
                      onChange={() => toggle(item.id)}
                    />
                    <span className="relative mt-0.5 shrink-0">
                      <Icon className={clsx('h-4 w-4', item.severity === 'ATENCAO' ? 'text-yellow-500' : 'text-gray-400')} />
                      {!item.read && (
                        <span className="absolute -left-2 top-1 h-1.5 w-1.5 rounded-full bg-accent" aria-label="Não lida" />
                      )}
                    </span>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-start justify-between gap-2">
                        <p className={clsx('truncate text-sm', item.read ? 'text-gray-300' : 'font-semibold text-white')}>
                          {item.title}
                        </p>
                        <span className="shrink-0 text-[11px] text-gray-500">{when(item.createdAt)}</span>
                      </div>
                      <div className="mt-1 flex items-center gap-2">
                        <span className="truncate text-xs text-gray-500">{item.body}</span>
                        {item.status && (
                          <Badge variant={STATUS[item.status].variant} className="shrink-0 px-2 py-0 text-[10px]">
                            {STATUS[item.status].label}
                          </Badge>
                        )}
                        {item.audience === 'ADMIN' && (
                          <Badge variant="info" className="shrink-0 px-2 py-0 text-[10px]">Admin</Badge>
                        )}
                      </div>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}

          {data && totalPages > 1 && (
            <div className="flex items-center justify-between border-t border-surface-light px-4 py-2 text-xs text-gray-400">
              <button
                className="flex items-center gap-1 disabled:opacity-40"
                disabled={data.page === 0}
                onClick={() => setFilter({ page: data.page - 1 })}
              >
                <ChevronLeft className="h-4 w-4" /> Anterior
              </button>
              <span>Página {data.page + 1} de {totalPages}</span>
              <button
                className="flex items-center gap-1 disabled:opacity-40"
                disabled={data.page + 1 >= totalPages}
                onClick={() => setFilter({ page: data.page + 1 })}
              >
                Próxima <ChevronRight className="h-4 w-4" />
              </button>
            </div>
          )}
        </Card>

        {/* Leitura */}
        <Card className={clsx('min-h-[320px]', !selected && 'hidden lg:block')}>
          {!selected ? (
            <div className="flex h-full min-h-[280px] flex-col items-center justify-center gap-2 text-sm text-gray-500">
              <Mail className="h-8 w-8 text-gray-600" />
              Selecione uma mensagem para ler.
            </div>
          ) : (
            <Reading
              item={selected}
              busy={state.isPending || status.isPending}
              onBack={() => setSelected(null)}
              onAction={(a) => runAction(selected, a)}
              onState={(action) => (action === 'DELETE'
                ? setConfirmDelete([selected.id])
                : state.mutate({ ids: [selected.id], action }))}
              onStatus={(action) => status.mutate({ id: selected.id, action })}
            />
          )}
        </Card>
      </div>

      <Modal
        isOpen={confirmDelete !== null}
        onClose={() => setConfirmDelete(null)}
        title="Excluir mensagens"
        footer={(
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setConfirmDelete(null)}>Cancelar</Button>
            <Button
              variant="danger"
              isLoading={state.isPending}
              onClick={() => {
                if (confirmDelete) state.mutate({ ids: confirmDelete, action: 'DELETE' });
                setConfirmDelete(null);
              }}
            >
              Excluir
            </Button>
          </div>
        )}
      >
        <p className="text-sm text-gray-300">
          {confirmDelete?.length === 1 ? 'A mensagem some' : `As ${confirmDelete?.length} mensagens somem`} da sua caixa e
          não podem ser restauradas. Se for uma pendência, a situação dela não muda.
        </p>
      </Modal>

      {informCost && (
        <InformCostModal
          isOpen
          params={informCost.action.params ?? {}}
          onClose={() => setInformCost(null)}
          onSaved={() => { setInformCost(null); refresh(); queryClient.invalidateQueries({ queryKey: ['portfolio'] }); }}
        />
      )}
    </div>
  );
};

const IconButton = ({ title, onClick, danger, children }: {
  title: string; onClick: () => void; danger?: boolean; children: ReactNode;
}) => (
  <button
    type="button"
    title={title}
    aria-label={title}
    onClick={onClick}
    className={clsx(
      'rounded-md p-1.5 transition-colors',
      danger ? 'text-gray-400 hover:bg-danger/10 hover:text-danger' : 'text-gray-400 hover:bg-white/5 hover:text-white'
    )}
  >
    {children}
  </button>
);

interface ReadingProps {
  item: NotificationItem;
  busy: boolean;
  onBack: () => void;
  onAction: (a: NotificationAction) => void;
  onState: (a: NotificationStateAction) => void;
  onStatus: (a: 'DISMISS' | 'REOPEN') => void;
}

const Reading = ({ item, busy, onBack, onAction, onState, onStatus }: ReadingProps) => {
  const cat = CATEGORY[item.category];
  const Icon = cat?.icon ?? Building2;
  const lines = item.payload?.lines ?? [];
  const actions = item.payload?.actions ?? [];

  return (
    <article className="space-y-5">
      <div className="flex items-center justify-between gap-2">
        <button onClick={onBack} className="flex items-center gap-1 text-sm text-gray-400 hover:text-white lg:hidden">
          <ArrowLeft className="h-4 w-4" /> Voltar
        </button>
        <div className="ml-auto flex items-center gap-1">
          <IconButton title="Marcar como não lida" onClick={() => onState('UNREAD')}>
            <Mail className="h-4 w-4" />
          </IconButton>
          {item.archived ? (
            <IconButton title="Desarquivar" onClick={() => onState('UNARCHIVE')}>
              <ArchiveRestore className="h-4 w-4" />
            </IconButton>
          ) : (
            <IconButton title="Arquivar" onClick={() => onState('ARCHIVE')}>
              <Archive className="h-4 w-4" />
            </IconButton>
          )}
          <IconButton title="Excluir" danger onClick={() => onState('DELETE')}>
            <Trash2 className="h-4 w-4" />
          </IconButton>
        </div>
      </div>

      <header className="space-y-2">
        <div className="flex flex-wrap items-center gap-2 text-xs text-gray-500">
          <Icon className={clsx('h-4 w-4', item.severity === 'ATENCAO' ? 'text-yellow-500' : 'text-gray-400')} />
          <span>{cat?.label}</span>
          <span>·</span>
          <span>{formatDateTime(item.createdAt)}</span>
          {item.audience === 'ADMIN' && <Badge variant="info" className="px-2 py-0 text-[10px]">Administração</Badge>}
          {item.status && (
            <Badge variant={STATUS[item.status].variant} className="px-2 py-0 text-[10px]">
              {STATUS[item.status].label}
            </Badge>
          )}
        </div>
        <h3 className="text-lg font-semibold text-white">{item.title}</h3>
      </header>

      <p className="whitespace-pre-line text-sm leading-relaxed text-gray-300">{item.body}</p>

      {lines.length > 0 && (
        <ul className="space-y-1 rounded-lg border border-surface-light bg-black/20 p-3 text-sm text-gray-300">
          {lines.map((l, i) => <li key={i} className="tabular-nums">{l}</li>)}
        </ul>
      )}

      {(actions.length > 0 || item.status === 'NAO_RESOLVIDA' || item.status === 'DISPENSADA') && (
        <div className="flex flex-wrap gap-2 border-t border-surface-light pt-4">
          {actions.map((a, i) => (
            <Button key={i} size="sm" variant={i === 0 ? 'primary' : 'outline'} onClick={() => onAction(a)}>
              {a.label}
            </Button>
          ))}
          {item.status === 'NAO_RESOLVIDA' && (
            <Button size="sm" variant="ghost" disabled={busy} onClick={() => onStatus('DISMISS')}
              title="Encerra a pendência sem tratar o assunto. Dá para reabrir depois.">
              <CircleSlash className="mr-1.5 h-4 w-4" /> Dispensar
            </Button>
          )}
          {item.status === 'DISPENSADA' && (
            <Button size="sm" variant="ghost" disabled={busy} onClick={() => onStatus('REOPEN')}>
              <RotateCcw className="mr-1.5 h-4 w-4" /> Reabrir
            </Button>
          )}
        </div>
      )}
    </article>
  );
};
