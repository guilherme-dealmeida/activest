import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, ExternalLink, History, KeyRound, Plug, Plus, RefreshCw, Trash2, Undo2 } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { ConnectSourceModal } from '@/components/integrations/ConnectSourceModal';
import { SourceLogo } from '@/components/integrations/SourceLogo';
import { ExclusionsModal } from '@/components/integrations/ExclusionsModal';
import { integrationsApi } from '@/api/integrations';
import { useToast } from '@/context/ToastContext';
import { useIntegrationSources } from '@/hooks/useIntegrationSources';
import { apiMessage } from '@/utils/apiError';
import {
  CONNECTION_STATUS, RUN_STATUS, formatDateTime, isRunInProgress, providerName, sourceOf
} from '@/utils/integrations';
import { IntegrationConnection, IntegrationSource } from '@/types';

/**
 * Integrações com corretoras e com a B3 (docs/planos/PLANO_CRIPTO_BINANCE.md e PLANO_INTEGRACAO_B3.md, F6):
 * conectar, pedir a sincronização, acompanhar a busca e chegar à pré-visualização.
 */
export const IntegrationsPage = () => {
  const navigate = useNavigate();
  const [choosing, setChoosing] = useState(false);
  const [connecting, setConnecting] = useState<IntegrationSource | null>(null);
  const [exclusionsOf, setExclusionsOf] = useState<IntegrationConnection | null>(null);
  const [disconnecting, setDisconnecting] = useState<IntegrationConnection | null>(null);

  const { data: connections, isLoading } = useQuery({
    queryKey: ['integrations'],
    queryFn: integrationsApi.list,
    // Enquanto alguma busca roda, a lista se atualiza sozinha.
    refetchInterval: (q) => (q.state.data?.some(c => isRunInProgress(c.lastRunStatus)) ? 3000 : false),
  });
  // Catálogo de fontes do servidor: "Conectar" sempre abre a escolha (docs/planos/PLANO_INTEGRACOES.md, 10.1).
  const sources = useIntegrationSources();
  const connect = () => setChoosing(true);
  const choose = (s: IntegrationSource) => { setChoosing(false); setConnecting(s); };

  if (isLoading) {
    return <div className="flex h-full items-center justify-center"><LoadingSpinner className="h-8 w-8 text-accent-text" /></div>;
  }

  const options = connections ?? [];

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start gap-4">
        <p className="max-w-2xl text-sm text-content-3">
          Conecte uma fonte <span className="text-content">só de leitura</span> e o Activest importa as suas compras e
          vendas. Você confere tudo antes de entrar na carteira; depois da primeira importação, a sincronização diária
          (08h45) traz o que for novo sozinha.
        </p>
        <Button className="ml-auto" onClick={connect}>
          <Plus className="mr-2 h-4 w-4" /> Conectar
        </Button>
      </div>

      {options.length === 0 ? (
        <Card className="flex flex-col items-center gap-3 py-12 text-center">
          <Plug className="h-10 w-10 text-accent-text" />
          <p className="text-content font-medium">Nenhuma fonte conectada</p>
          <p className="max-w-md text-sm text-content-3">
            Conecte uma corretora ou a bolsa e as suas operações chegam sozinhas. A credencial fica cifrada e só permite
            leitura: o Activest não negocia, não saca e não transfere nada.
          </p>
          <Button onClick={connect}><Plus className="mr-2 h-4 w-4" /> Conectar</Button>
        </Card>
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          {options.map(c => (
            <ConnectionCard key={c.id} connection={c} source={sourceOf(c.provider, sources)}
              onExclusions={() => setExclusionsOf(c)}
              onDisconnect={() => setDisconnecting(c)}
              onReconnect={() => { const s = sourceOf(c.provider, sources); if (s?.available) setConnecting(s); }} />
          ))}
        </div>
      )}

      <Modal isOpen={choosing} onClose={() => setChoosing(false)} title="Escolha a integração" size="lg">
        {!sources ? (
          <div className="flex justify-center py-6"><LoadingSpinner className="h-6 w-6 text-accent-text" /></div>
        ) : (
          <div className="grid gap-3 sm:grid-cols-2">
            {sources.map(s => (
              <button key={s.provider} type="button" disabled={!s.available} onClick={() => choose(s)}
                className={`flex items-start gap-3 rounded-control border border-line px-4 py-3 text-left transition-colors ${
                  s.available ? 'hover:border-accent' : 'cursor-not-allowed opacity-60'}`}>
                <SourceLogo source={s} />
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="text-content font-medium">{s.name}</p>
                    {!s.available && <Badge variant="default">Indisponível no momento</Badge>}
                  </div>
                  <p className="text-xs text-accent-text">{s.category}</p>
                  <p className="mt-1 text-xs text-content-3">{s.description}</p>
                </div>
              </button>
            ))}
          </div>
        )}
      </Modal>
      <ConnectSourceModal
        source={connecting}
        onClose={() => setConnecting(null)}
        onConnected={() => setConnecting(null)}
      />
      <ExclusionsModal connection={exclusionsOf} onClose={() => setExclusionsOf(null)} />
      <DisconnectModal connection={disconnecting} source={sourceOf(disconnecting?.provider, sources)}
        onClose={() => setDisconnecting(null)}
        onDone={() => { setDisconnecting(null); navigate('/integrations'); }} />
    </div>
  );
};

// ---------------------------------------------------------------------------

interface CardProps {
  connection: IntegrationConnection;
  source?: IntegrationSource;
  onExclusions: () => void;
  onDisconnect: () => void;
  onReconnect: () => void;
}

const ConnectionCard = ({ connection: c, source, onExclusions, onDisconnect, onReconnect }: CardProps) => {
  const name = source?.name ?? providerName(c.provider);
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [showHistory, setShowHistory] = useState(false);
  const revoked = c.status === 'REVOKED';
  const awaiting = c.status === 'PENDING_AUTHORIZATION';
  const searching = isRunInProgress(c.lastRunStatus);

  // Avisa quando a busca termina, mesmo que o usuário tenha ficado na lista.
  const previous = useRef(c.lastRunStatus);
  useEffect(() => {
    const before = previous.current;
    previous.current = c.lastRunStatus;
    if (!isRunInProgress(before) || isRunInProgress(c.lastRunStatus)) return;
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

  const { data: history } = useQuery({
    queryKey: ['integrations', c.id, 'runs'],
    queryFn: () => integrationsApi.runs(c.id),
    enabled: showHistory,
  });

  const status = CONNECTION_STATUS[c.status];
  const run = c.lastRunStatus ? RUN_STATUS[c.lastRunStatus] : null;

  return (
    <Card className={revoked ? 'opacity-70' : undefined}>
      <div className="flex items-start gap-3">
        <SourceLogo source={source} fallback={name} />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-content font-semibold truncate">{c.label}</p>
            <Badge variant={status.variant}>{status.label}</Badge>
          </div>
          <p className="mt-1 flex items-center gap-2 text-xs text-content-3">
            {name}
            {c.fingerprint && (source?.credential === 'DOCUMENT'
              ? <span>CPF ***.***.***-{c.fingerprint}</span>
              : <><KeyRound className="h-3 w-3" /> ****{c.fingerprint}</>)}
            <span>· Última sincronização: {formatDateTime(c.lastSyncAt)}</span>
          </p>
        </div>
      </div>

      {!revoked && (
        <div className="mt-4 space-y-3">
          {run && (
            <div className="flex flex-wrap items-center gap-2 text-sm">
              <span className="text-content-3">Última busca:</span>
              <Badge variant={run.variant}>
                {searching && <LoadingSpinner className="mr-1 h-3 w-3" />}
                {run.label}
              </Badge>
              {c.lastRunStatus === 'AWAITING_CONFIRMATION' && c.lastRunId && (
                <Link to={`/integrations/${c.id}/runs/${c.lastRunId}`} className="text-accent-text hover:underline">
                  Ver pré-visualização
                </Link>
              )}
              {(c.lastRunStatus === 'SUCCEEDED' || c.lastRunStatus === 'FAILED') && c.lastRunId && (
                <Link to={`/integrations/${c.id}/runs/${c.lastRunId}`} className="text-content-2 hover:text-content hover:underline">
                  Ver resultado
                </Link>
              )}
            </div>
          )}
          {searching && (
            <p className="text-xs text-content-3">
              Buscando na {name}… a primeira busca pode levar cerca de 2 minutos. Pode sair desta tela.
            </p>
          )}
          {awaiting && !searching && (
            <div className="space-y-2 rounded-control border border-accent/30 bg-accent/10 px-3 py-2 text-xs text-accent-text">
              <p>
                Falta autorizar o Activest na {name}. Entre com o seu login, autorize e volte aqui
                para sincronizar.
              </p>
              {c.authorizationUrl && (
                <a href={c.authorizationUrl} target="_blank" rel="noopener noreferrer"
                  className="inline-flex items-center gap-1 font-medium text-accent-text hover:underline">
                  Autorizar na {name} <ExternalLink className="h-3 w-3" />
                </a>
              )}
            </div>
          )}
          {c.lastError && !searching && !awaiting && (
            <p className="rounded-control border border-danger/30 bg-danger/10 px-3 py-2 text-xs text-danger">{c.lastError}</p>
          )}
          {c.divergenceCount > 0 && (
            <p className="flex items-center gap-2 text-sm text-accent-text">
              <AlertTriangle className="h-4 w-4" />
              {c.divergenceCount === 1 ? '1 ativo diverge' : `${c.divergenceCount} ativos divergem`} da {name}
              {/* A divergência é da última execução confirmada: se é a última
                  busca, o "ver" abre ela; senão, o histórico mostra qual. */}
              {c.lastRunStatus === 'SUCCEEDED' && c.lastRunId ? (
                <Link to={`/integrations/${c.id}/runs/${c.lastRunId}`} className="text-accent-text hover:underline">ver</Link>
              ) : (
                <button className="text-accent-text hover:underline" onClick={() => setShowHistory(true)}>ver</button>
              )}
            </p>
          )}

          <div className="flex flex-wrap gap-2 pt-1">
            <Button size="sm" onClick={() => sync.mutate()} disabled={searching} isLoading={sync.isPending}>
              <RefreshCw className="mr-1.5 h-4 w-4" /> {awaiting ? 'Já autorizei — sincronizar' : 'Sincronizar'}
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
            <div className="rounded-control border border-line">
              {!history ? (
                <div className="flex justify-center py-4"><LoadingSpinner className="h-5 w-5 text-accent-text" /></div>
              ) : history.length === 0 ? (
                <p className="py-4 text-center text-xs text-content-4">Nenhuma sincronização ainda.</p>
              ) : (
                <table className="w-full text-xs">
                  <thead className="uppercase text-content-3 border-b border-line">
                    <tr>
                      <th className="px-3 py-2 text-left">Quando</th>
                      <th className="px-3 py-2 text-left">Origem</th>
                      <th className="px-3 py-2 text-left">Situação</th>
                      <th className="px-3 py-2 text-right">Importadas</th>
                      <th className="px-3 py-2" />
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line-soft">
                    {history.map(h => (
                      <tr key={h.runId}>
                        <td className="px-3 py-2">{formatDateTime(h.createdAt)}</td>
                        <td className="px-3 py-2 text-content-3">{h.trigger === 'SCHEDULED' ? 'Diária' : 'Manual'}</td>
                        <td className="px-3 py-2"><Badge variant={RUN_STATUS[h.status].variant}>{RUN_STATUS[h.status].label}</Badge></td>
                        <td className="px-3 py-2 text-right">{h.importedCount}</td>
                        <td className="px-3 py-2 text-right">
                          {!isRunInProgress(h.status) && (
                            <Link to={`/integrations/${c.id}/runs/${h.runId}`} className="text-accent-text hover:underline">abrir</Link>
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

      {revoked && (
        <div className="mt-4 flex items-center gap-3 text-sm text-content-3">
          {c.lastError ? `${c.lastError} ` : 'Desconectada: a credencial foi apagada. '}
          As operações importadas continuam na carteira.
          <Button size="sm" variant="outline" className="ml-auto" onClick={onReconnect}
            disabled={!source?.available}>Conectar de novo</Button>
        </div>
      )}
    </Card>
  );
};

// ---------------------------------------------------------------------------

const DisconnectModal = ({ connection, source, onClose, onDone }: {
  connection: IntegrationConnection | null; source?: IntegrationSource; onClose: () => void; onDone: () => void;
}) => {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const mutation = useMutation({
    mutationFn: () => integrationsApi.disconnect(connection!.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['integrations'] });
      showToast(`"${connection?.label}" desconectada. A credencial foi apagada.`, 'success');
      onDone();
    },
    onError: (e: unknown) => showToast(apiMessage(e) ?? 'Não foi possível desconectar.', 'error', 8000),
  });
  return (
    <Modal isOpen={!!connection} onClose={onClose} title="Desconectar"
      footer={
        <div className="flex justify-end gap-3">
          <Button variant="ghost" onClick={onClose}>Cancelar</Button>
          <Button variant="danger" onClick={() => mutation.mutate()} isLoading={mutation.isPending}>Desconectar</Button>
        </div>
      }>
      <p className="text-sm text-content-2">
        A credencial de <span className="text-content font-medium">{connection?.label}</span>
        {source?.credential === 'DOCUMENT' ? ' (o CPF)' : ' (a chave)'} será apagada na hora e as sincronizações param. As operações já importadas <span className="text-content">continuam na carteira</span>;
        se quiser tirá-las, exclua pela tela de Transações.
      </p>
    </Modal>
  );
};
