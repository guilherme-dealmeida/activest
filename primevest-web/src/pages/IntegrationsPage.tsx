import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, History, KeyRound, Plug, Plus, RefreshCw, Trash2, Undo2 } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { ConnectBinanceModal } from '@/components/integrations/ConnectBinanceModal';
import { ExclusionsModal } from '@/components/integrations/ExclusionsModal';
import { integrationsApi } from '@/api/integrations';
import { useToast } from '@/context/ToastContext';
import { apiMessage } from '@/utils/apiError';
import {
  CONNECTION_STATUS, RUN_STATUS, formatDateTime, isRunInProgress, providerName
} from '@/utils/integrations';
import { IntegrationConnection } from '@/types';

/**
 * Integrações com corretoras (docs/planos/PLANO_CRIPTO_BINANCE.md, F6): conectar, pedir
 * a sincronização, acompanhar a busca e chegar à pré-visualização.
 */
export const IntegrationsPage = () => {
  const navigate = useNavigate();
  const [connectOpen, setConnectOpen] = useState(false);
  const [exclusionsOf, setExclusionsOf] = useState<IntegrationConnection | null>(null);
  const [disconnecting, setDisconnecting] = useState<IntegrationConnection | null>(null);

  const { data: connections, isLoading } = useQuery({
    queryKey: ['integrations'],
    queryFn: integrationsApi.list,
    // Enquanto alguma busca roda, a lista se atualiza sozinha.
    refetchInterval: (q) => (q.state.data?.some(c => isRunInProgress(c.lastRunStatus)) ? 3000 : false),
  });

  if (isLoading) {
    return <div className="flex h-full items-center justify-center"><LoadingSpinner className="h-8 w-8 text-accent" /></div>;
  }

  const lista = connections ?? [];

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start gap-4">
        <p className="max-w-2xl text-sm text-gray-400">
          Conecte sua conta de uma corretora com uma chave <span className="text-white">só de leitura</span> e o PrimeVest
          importa suas compras e vendas de cripto. Você confere tudo antes de entrar na carteira; depois da primeira
          importação, a sincronização diária (06h30) traz o que for novo sozinha.
        </p>
        <Button className="ml-auto" onClick={() => setConnectOpen(true)}>
          <Plus className="mr-2 h-4 w-4" /> Conectar corretora
        </Button>
      </div>

      {lista.length === 0 ? (
        <Card className="flex flex-col items-center gap-3 py-12 text-center">
          <Plug className="h-10 w-10 text-accent" />
          <p className="text-white font-medium">Nenhuma corretora conectada</p>
          <p className="max-w-md text-sm text-gray-400">
            Hoje a Binance está disponível. A chave fica cifrada e só permite leitura: o PrimeVest não negocia, não
            saca e não transfere nada.
          </p>
          <Button onClick={() => setConnectOpen(true)}><Plus className="mr-2 h-4 w-4" /> Conectar Binance</Button>
        </Card>
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          {lista.map(c => (
            <ConnectionCard key={c.id} connection={c}
              onExclusions={() => setExclusionsOf(c)}
              onDisconnect={() => setDisconnecting(c)}
              onReconnect={() => setConnectOpen(true)} />
          ))}
        </div>
      )}

      <ConnectBinanceModal
        isOpen={connectOpen}
        onClose={() => setConnectOpen(false)}
        onConnected={() => setConnectOpen(false)}
      />
      <ExclusionsModal connection={exclusionsOf} onClose={() => setExclusionsOf(null)} />
      <DisconnectModal connection={disconnecting} onClose={() => setDisconnecting(null)}
        onDone={() => { setDisconnecting(null); navigate('/integrations'); }} />
    </div>
  );
};

// ---------------------------------------------------------------------------

interface CardProps {
  connection: IntegrationConnection;
  onExclusions: () => void;
  onDisconnect: () => void;
  onReconnect: () => void;
}

const ConnectionCard = ({ connection: c, onExclusions, onDisconnect, onReconnect }: CardProps) => {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [showHistory, setShowHistory] = useState(false);
  const revogada = c.status === 'REVOKED';
  const buscando = isRunInProgress(c.lastRunStatus);

  // Avisa quando a busca termina, mesmo que o usuário tenha ficado na lista.
  const anterior = useRef(c.lastRunStatus);
  useEffect(() => {
    const antes = anterior.current;
    anterior.current = c.lastRunStatus;
    if (!isRunInProgress(antes) || isRunInProgress(c.lastRunStatus)) return;
    if (c.lastRunStatus === 'AWAITING_CONFIRMATION') {
      showToast(`${c.label}: busca concluída. Confira a pré-visualização.`, 'success');
    } else if (c.lastRunStatus === 'FAILED') {
      showToast(`${c.label}: a busca falhou. ${c.lastError ?? ''}`, 'error', 8000);
    }
  }, [c.lastRunStatus, c.label, c.lastError, showToast]);

  const sync = useMutation({
    mutationFn: () => integrationsApi.sync(c.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['integrations'] });
      showToast('Sincronização na fila. A primeira busca pode levar cerca de 2 minutos.', 'info');
    },
    onError: (e: unknown) => showToast(apiMessage(e) ?? 'Não foi possível sincronizar.', 'error', 8000),
  });

  const { data: historico } = useQuery({
    queryKey: ['integrations', c.id, 'runs'],
    queryFn: () => integrationsApi.runs(c.id),
    enabled: showHistory,
  });

  const status = CONNECTION_STATUS[c.status];
  const run = c.lastRunStatus ? RUN_STATUS[c.lastRunStatus] : null;

  return (
    <Card className={revogada ? 'opacity-70' : undefined}>
      <div className="flex items-start gap-3">
        <div className="flex h-10 w-10 items-center justify-center rounded-full bg-accent/10 text-accent font-bold">
          {providerName(c.provider).charAt(0)}
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-white font-semibold truncate">{c.label}</p>
            <Badge variant={status.variant}>{status.label}</Badge>
          </div>
          <p className="mt-1 flex items-center gap-2 text-xs text-gray-400">
            {providerName(c.provider)}
            {c.fingerprint && (<><KeyRound className="h-3 w-3" /> ****{c.fingerprint}</>)}
            <span>· Última sincronização: {formatDateTime(c.lastSyncAt)}</span>
          </p>
        </div>
      </div>

      {!revogada && (
        <div className="mt-4 space-y-3">
          {run && (
            <div className="flex flex-wrap items-center gap-2 text-sm">
              <span className="text-gray-400">Última busca:</span>
              <Badge variant={run.variant}>
                {buscando && <LoadingSpinner className="mr-1 h-3 w-3" />}
                {run.label}
              </Badge>
              {c.lastRunStatus === 'AWAITING_CONFIRMATION' && c.lastRunId && (
                <Link to={`/integrations/${c.id}/runs/${c.lastRunId}`} className="text-accent hover:underline">
                  Ver pré-visualização
                </Link>
              )}
              {(c.lastRunStatus === 'SUCCEEDED' || c.lastRunStatus === 'FAILED') && c.lastRunId && (
                <Link to={`/integrations/${c.id}/runs/${c.lastRunId}`} className="text-gray-300 hover:text-white hover:underline">
                  Ver resultado
                </Link>
              )}
            </div>
          )}
          {buscando && (
            <p className="text-xs text-gray-400">
              Buscando na {providerName(c.provider)}… a primeira busca pode levar cerca de 2 minutos. Pode sair desta tela.
            </p>
          )}
          {c.lastError && !buscando && (
            <p className="rounded-md border border-danger/30 bg-danger/10 px-3 py-2 text-xs text-danger">{c.lastError}</p>
          )}
          {c.divergenceCount > 0 && (
            <p className="flex items-center gap-2 text-sm text-yellow-500">
              <AlertTriangle className="h-4 w-4" />
              {c.divergenceCount === 1 ? '1 ativo diverge' : `${c.divergenceCount} ativos divergem`} da {providerName(c.provider)}
              {/* A divergência é da última execução confirmada: se é a última
                  busca, o "ver" abre ela; senão, o histórico mostra qual. */}
              {c.lastRunStatus === 'SUCCEEDED' && c.lastRunId ? (
                <Link to={`/integrations/${c.id}/runs/${c.lastRunId}`} className="text-accent hover:underline">ver</Link>
              ) : (
                <button className="text-accent hover:underline" onClick={() => setShowHistory(true)}>ver</button>
              )}
            </p>
          )}

          <div className="flex flex-wrap gap-2 pt-1">
            <Button size="sm" onClick={() => sync.mutate()} disabled={buscando} isLoading={sync.isPending}>
              <RefreshCw className="mr-1.5 h-4 w-4" /> Sincronizar
            </Button>
            <Button size="sm" variant="outline" onClick={() => setShowHistory(v => !v)}>
              <History className="mr-1.5 h-4 w-4" /> Histórico
            </Button>
            <Button size="sm" variant="outline" onClick={onExclusions}>
              <Undo2 className="mr-1.5 h-4 w-4" /> Excluídas
            </Button>
            <Button size="sm" variant="ghost" className="ml-auto text-danger" onClick={onDisconnect}>
              <Trash2 className="mr-1.5 h-4 w-4" /> Desconectar
            </Button>
          </div>

          {showHistory && (
            <div className="rounded-md border border-surface-light">
              {!historico ? (
                <div className="flex justify-center py-4"><LoadingSpinner className="h-5 w-5 text-accent" /></div>
              ) : historico.length === 0 ? (
                <p className="py-4 text-center text-xs text-gray-500">Nenhuma sincronização ainda.</p>
              ) : (
                <table className="w-full text-xs">
                  <thead className="uppercase text-gray-400 border-b border-white/10">
                    <tr>
                      <th className="px-3 py-2 text-left">Quando</th>
                      <th className="px-3 py-2 text-left">Origem</th>
                      <th className="px-3 py-2 text-left">Situação</th>
                      <th className="px-3 py-2 text-right">Importadas</th>
                      <th className="px-3 py-2" />
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-white/5">
                    {historico.map(h => (
                      <tr key={h.runId}>
                        <td className="px-3 py-2">{formatDateTime(h.createdAt)}</td>
                        <td className="px-3 py-2 text-gray-400">{h.trigger === 'SCHEDULED' ? 'Diária' : 'Manual'}</td>
                        <td className="px-3 py-2"><Badge variant={RUN_STATUS[h.status].variant}>{RUN_STATUS[h.status].label}</Badge></td>
                        <td className="px-3 py-2 text-right">{h.importedCount}</td>
                        <td className="px-3 py-2 text-right">
                          {!isRunInProgress(h.status) && (
                            <Link to={`/integrations/${c.id}/runs/${h.runId}`} className="text-accent hover:underline">abrir</Link>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          )}
        </div>
      )}

      {revogada && (
        <div className="mt-4 flex items-center gap-3 text-sm text-gray-400">
          Desconectada: a chave foi apagada. As operações importadas continuam na carteira.
          <Button size="sm" variant="outline" className="ml-auto" onClick={onReconnect}>Conectar de novo</Button>
        </div>
      )}
    </Card>
  );
};

// ---------------------------------------------------------------------------

const DisconnectModal = ({ connection, onClose, onDone }: {
  connection: IntegrationConnection | null; onClose: () => void; onDone: () => void;
}) => {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const mutation = useMutation({
    mutationFn: () => integrationsApi.disconnect(connection!.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['integrations'] });
      showToast(`"${connection?.label}" desconectada. A chave foi apagada.`, 'success');
      onDone();
    },
    onError: (e: unknown) => showToast(apiMessage(e) ?? 'Não foi possível desconectar.', 'error', 8000),
  });
  return (
    <Modal isOpen={!!connection} onClose={onClose} title="Desconectar corretora"
      footer={
        <div className="flex justify-end gap-3">
          <Button variant="ghost" onClick={onClose}>Cancelar</Button>
          <Button variant="danger" onClick={() => mutation.mutate()} isLoading={mutation.isPending}>Desconectar</Button>
        </div>
      }>
      <p className="text-sm text-gray-300">
        A chave de <span className="text-white font-medium">{connection?.label}</span> será apagada na hora e as
        sincronizações param. As operações já importadas <span className="text-white">continuam na carteira</span>;
        se quiser tirá-las, exclua pela tela de Transações.
      </p>
    </Modal>
  );
};
