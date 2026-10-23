import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, CheckCircle2 } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { DivergenceList } from '@/components/integrations/DivergenceList';
import { integrationsApi } from '@/api/integrations';
import { useToast } from '@/context/ToastContext';
import { apiMessage } from '@/utils/apiError';
import { formatCurrency, formatDate, formatQuantity, formatUnitPrice } from '@/utils/format';
import { KIND, RESOLUTION, RUN_STATUS, formatDateTime, isRunInProgress, providerName } from '@/utils/integrations';
import { DuplicateDecision, ImportRunItem, MovementKind, PreviewOutcome } from '@/types';

/** Ordem dos grupos na tela: o que entra primeiro, o que precisa de decisão logo depois. */
const GROUPS: { outcome: PreviewOutcome; titleText: string; info: string }[] = [
  { outcome: 'POSSIBLE_DUPLICATE', titleText: 'Possíveis duplicatas',
    info: 'Parecem com um lançamento que você digitou (mesmo ativo e tipo, até 1 dia de diferença, quantidade compatível). Decida cada uma.' },
  { outcome: 'NEW', titleText: 'Novas', info: 'Entram na carteira ao confirmar.' },
  { outcome: 'UNMAPPED', titleText: 'Não importadas', info: 'Ficam de fora, com o motivo. Nada é descartado em silêncio.' },
  { outcome: 'EXISTING', titleText: 'Já importadas', info: 'Já estão na carteira desde uma sincronização anterior.' },
  { outcome: 'EXCLUDED', titleText: 'Excluídas por você', info: 'Você excluiu estas operações; elas não voltam. Restaure em Integrações › Excluídas.' },
];

const DECISIONS: { value: DuplicateDecision; label: string }[] = [
  { value: 'REPLACE', label: 'Substituir o manual' },
  { value: 'KEEP_BOTH', label: 'Manter os dois' },
  { value: 'SKIP', label: 'Ignorar esta' },
];

/**
 * Pré-visualização e resultado de uma sincronização (docs/planos/PLANO_CRIPTO_BINANCE.md,
 * F6). Página própria: a primeira importação de outro usuário pode ter
 * centenas de linhas.
 */
export const IntegrationRunPage = () => {
  const { id = '', runId = '' } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [decisions, setDecisions] = useState<Record<string, DuplicateDecision>>({});

  const { data: run, isLoading } = useQuery({
    queryKey: ['integrations', id, 'run', runId],
    queryFn: () => integrationsApi.getRun(id, runId),
    refetchInterval: (q) => (isRunInProgress(q.state.data?.status) ? 3000 : false),
  });

  const { data: connections } = useQuery({ queryKey: ['integrations'], queryFn: integrationsApi.list });
  const providerCode = connections?.find(c => c.id === id)?.provider ?? 'BINANCE';
  const provider = providerName(providerCode);
  // A B3 entrega a data do negócio, sem hora (B2).
  const soData = providerCode === 'B3';

  // Quando a busca termina com a tela aberta, a lista de conexões também muda.
  useEffect(() => {
    if (run && !isRunInProgress(run.status)) {
      queryClient.invalidateQueries({ queryKey: ['integrations'], exact: true });
    }
  }, [run?.status, run, queryClient]);

  const byGroup = useMemo(() => {
    const m = new Map<PreviewOutcome, ImportRunItem[]>();
    (run?.items ?? []).forEach(i => m.set(i.outcome, [...(m.get(i.outcome) ?? []), i]));
    return m;
  }, [run]);

  const duplicates = byGroup.get('POSSIBLE_DUPLICATE') ?? [];
  const pendingDecisions = duplicates.filter(d => !decisions[d.externalId]).length;
  const awaiting = run?.status === 'AWAITING_CONFIRMATION';

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['integrations'] });
    queryClient.invalidateQueries({ queryKey: ['transactions'] });
    queryClient.invalidateQueries({ queryKey: ['portfolio'] });
  };

  const doConfirm = useMutation({
    mutationFn: () => integrationsApi.confirm(id, runId, decisions),
    onSuccess: (r) => {
      invalidate();
      queryClient.setQueryData(['integrations', id, 'run', runId], r);
      const n = r.items.filter(i => i.resolution && i.resolution !== 'SKIPPED').length;
      showToast(n === 0 ? 'Sincronização confirmada. Nada novo a importar.'
        : `${n} ${n === 1 ? 'operação importada' : 'operações importadas'} para a carteira.`, 'success');
    },
    onError: (e: unknown) => {
      queryClient.invalidateQueries({ queryKey: ['integrations', id, 'run', runId] });
      showToast(apiMessage(e) ?? 'Não foi possível confirmar. Nada foi gravado.', 'error', 8000);
    },
  });

  const discard = useMutation({
    mutationFn: () => integrationsApi.cancel(id, runId),
    onSuccess: () => {
      invalidate();
      showToast('Pré-visualização descartada. Nada foi gravado.', 'info');
      navigate('/integrations');
    },
    onError: (e: unknown) => showToast(apiMessage(e) ?? 'Não foi possível descartar.', 'error', 8000),
  });

  if (isLoading || !run) {
    return <div className="flex h-full items-center justify-center"><LoadingSpinner className="h-8 w-8 text-accent" /></div>;
  }

  const status = RUN_STATUS[run.status];
  const summary: { label: string; amount: string | number; highlight?: boolean }[] = [
    { label: 'Novas', amount: run.newCount, highlight: true },
    { label: 'Possíveis duplicatas', amount: run.duplicateCount, highlight: run.duplicateCount > 0 },
    { label: 'Não importadas', amount: run.unmappedCount },
    { label: 'Já importadas', amount: run.existingCount },
    { label: 'Excluídas por você', amount: run.excludedCount },
    ...(providerCode === 'BINANCE' ? [{ label: 'Depósitos em reais', amount: formatCurrency(run.fiatDeposited ?? 0) }] : []),
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-3">
        <Link to="/integrations" className="flex items-center gap-1 text-sm text-gray-400 hover:text-white">
          <ArrowLeft className="h-4 w-4" /> Integrações
        </Link>
        <span className="text-white font-semibold">{run.connectionLabel}</span>
        <Badge variant={status.variant}>
          {isRunInProgress(run.status) && <LoadingSpinner className="mr-1 h-3 w-3" />}
          {status.label}
        </Badge>
        <span className="text-xs text-gray-400">
          {run.trigger === 'SCHEDULED' ? 'Sincronização diária' : 'Sincronização manual'} · {formatDateTime(run.createdAt)}
        </span>
      </div>

      {isRunInProgress(run.status) && (
        <Card className="flex items-center gap-3 text-sm text-gray-300">
          <LoadingSpinner className="h-5 w-5 text-accent" />
          Buscando na {provider}… a primeira busca pode levar cerca de 2 minutos. Esta tela atualiza sozinha.
        </Card>
      )}

      {run.error && !isRunInProgress(run.status) && (
        <div className="rounded-md border border-danger/30 bg-danger/10 px-4 py-3 text-sm text-danger">
          {run.status === 'FAILED' ? 'A busca falhou: ' : 'A última tentativa de confirmar falhou — nada foi gravado: '}
          {run.error}
        </div>
      )}

      {!isRunInProgress(run.status) && run.status !== 'FAILED' && (
        <>
          <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
            {summary.map(r => (
              <div key={r.label} className="rounded-xl border border-surface-light bg-surface p-4">
                <p className="text-xs text-gray-400">{r.label}</p>
                <p className={`mt-1 text-xl font-semibold ${r.highlight ? 'text-accent' : 'text-white'}`}>{r.amount}</p>
              </div>
            ))}
          </div>

          {run.status === 'SUCCEEDED' && (
            <>
              <div className="flex items-center gap-2 text-sm text-success">
                <CheckCircle2 className="h-4 w-4" /> Confirmada em {formatDateTime(run.finishedAt)}.
              </div>
              <DivergenceList divergences={run.divergences} provider={provider} />
            </>
          )}

          {GROUPS.map(g => {
            const itemList = byGroup.get(g.outcome) ?? [];
            if (itemList.length === 0) return null;
            return (
              <Card key={g.outcome} title={`${g.titleText} (${itemList.length})`} titleInfo={g.info}>
                <ItemsTable itemList={itemList} duplicate={g.outcome === 'POSSIBLE_DUPLICATE'} soData={soData}
                  decisions={decisions} canDecide={awaiting}
                  onDecide={(ext, d) => setDecisions(prev => ({ ...prev, [ext]: d }))} />
              </Card>
            );
          })}

          {run.items.length === 0 && (
            <Card className="py-8 text-center text-sm text-gray-400">Nenhuma operação nova na {provider} desde a última sincronização.</Card>
          )}

          {awaiting && (
            <div className="sticky bottom-0 flex flex-wrap items-center gap-3 rounded-xl border border-surface-light bg-surface p-4">
              <p className="text-sm text-gray-400">
                {pendingDecisions > 0
                  ? `Decida ${pendingDecisions === 1 ? 'a possível duplicata' : `as ${pendingDecisions} possíveis duplicatas`} para confirmar.`
                  : run.newCount + duplicates.length === 0
                    ? 'Nada a importar. Confirmar registra a sincronização.'
                    : 'Tudo ou nada: se uma operação for recusada, nada é gravado.'}
              </p>
              <div className="ml-auto flex gap-3">
                <Button variant="ghost" onClick={() => discard.mutate()} isLoading={discard.isPending}>Descartar</Button>
                <Button onClick={() => doConfirm.mutate()} disabled={pendingDecisions > 0} isLoading={doConfirm.isPending}>
                  Confirmar importação
                </Button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
};

// ---------------------------------------------------------------------------

interface TableProps {
  itemList: ImportRunItem[];
  duplicate: boolean;
  decisions: Record<string, DuplicateDecision>;
  canDecide: boolean;
  onDecide: (externalId: string, d: DuplicateDecision) => void;
  /** Fonte que informa so a data (B3): sem hora. */
  soData?: boolean;
}

/** Movimentos que são compra ou venda (e os que viram transação); os demais (proventos, eventos, liquidação...)
 *  são entrada ou saída. */
const NEGOCIACAO = new Set<MovementKind>(['TRADE', 'CONVERT', 'EQUITY_TRADE']);

const ItemsTable = ({ itemList, duplicate, decisions, canDecide, onDecide, soData }: TableProps) => (
  <div className="overflow-x-auto">
    <table className="w-full text-sm">
      <thead className="text-xs uppercase text-gray-400 border-b border-white/10">
        <tr>
          <th className="px-3 py-2 text-left">Data</th>
          <th className="px-3 py-2 text-left">Operação</th>
          <th className="px-3 py-2 text-left">Ativo</th>
          <th className="px-3 py-2 text-right">Quantidade</th>
          <th className="px-3 py-2 text-right">Preço</th>
          <th className="px-3 py-2 text-right">Taxa</th>
          <th className="px-3 py-2 text-right">Total</th>
          <th className="px-3 py-2 text-left">{duplicate ? 'Decisão' : 'Situação'}</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-white/5">
        {itemList.map(i => {
          const t = i.transaction;
          const sale = (t?.type ?? i.side) === 'SELL';
          return (
            <tr key={i.externalId} className="align-top">
              <td className="px-3 py-2 whitespace-nowrap">
                {t ? formatDate(t.transactionDate) : soData ? formatDateTime(i.executedAt).slice(0, 10) : formatDateTime(i.executedAt)}
              </td>
              <td className="px-3 py-2">
                <span className={sale ? 'text-danger' : 'text-success'}>
                  {!i.side ? '' : t || NEGOCIACAO.has(i.kind) ? (sale ? 'Venda' : 'Compra') : (sale ? 'Saída' : 'Entrada')}
                </span>
                <span className="ml-1 text-xs text-gray-400">{KIND[i.kind]}</span>
              </td>
              <td className="px-3 py-2 text-white font-medium">{t?.ticker ?? i.asset}</td>
              <td className="px-3 py-2 text-right">
                {t ? formatQuantity(t.shares, true) : i.grossQuantity != null ? formatQuantity(i.grossQuantity, true) : '—'}
              </td>
              <td className="px-3 py-2 text-right">{t ? formatUnitPrice(t.pricePerShare, true) : '—'}</td>
              <td className="px-3 py-2 text-right">{t ? formatCurrency(t.brokerageFee) : '—'}</td>
              <td className="px-3 py-2 text-right text-white">
                {t ? formatCurrency(t.totalAmount)
                  : i.quoteAmount == null ? '—'
                    : i.quoteAsset === 'BRL' ? formatCurrency(i.quoteAmount)
                      : `${formatQuantity(i.quoteAmount, true)} ${i.quoteAsset ?? ''}`}
              </td>
              <td className="px-3 py-2">
                {i.resolution ? (
                  <span className={i.resolution === 'SKIPPED' ? 'text-gray-400' : 'text-success'}>
                    {RESOLUTION[i.resolution]}
                    {i.transactionId && <Link to="/transactions" className="ml-2 text-xs text-accent hover:underline">ver</Link>}
                  </span>
                ) : duplicate ? (
                  <div className="space-y-2">
                    {i.duplicateOf && (
                      <p className="text-xs text-gray-400">
                        Manual de {formatDate(i.duplicateOf.transactionDate)}:{' '}
                        {i.duplicateOf.shares != null ? formatQuantity(i.duplicateOf.shares, true) : '—'}
                        {i.duplicateOf.pricePerShare != null ? ` a ${formatUnitPrice(i.duplicateOf.pricePerShare, true)}` : ''}
                      </p>
                    )}
                    <div className="flex flex-wrap gap-1">
                      {DECISIONS.map(d => (
                        <button key={d.value} type="button" disabled={!canDecide}
                          onClick={() => onDecide(i.externalId, d.value)}
                          className={`rounded-md border px-2 py-1 text-xs transition-colors ${
                            decisions[i.externalId] === d.value
                              ? 'border-accent bg-accent/10 text-accent'
                              : 'border-surface-light text-gray-300 hover:bg-white/5'}`}>
                          {d.label}
                        </button>
                      ))}
                    </div>
                  </div>
                ) : i.reason ? (
                  <span className="text-xs text-gray-400">{i.reason}</span>
                ) : t && t.roundingDifference !== 0 ? (
                  <span className="text-xs text-gray-400">Arredondamento de {formatCurrency(t.roundingDifference)}</span>
                ) : null}
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  </div>
);
