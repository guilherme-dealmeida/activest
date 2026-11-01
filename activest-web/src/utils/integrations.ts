import {
  ConnectionStatus, IntegrationConnection, IntegrationSource, MovementKind, RunItemResolution, RunStatus
} from '@/types';
import { formatCurrency, formatMoney, formatUnitPrice } from '@/utils/format';

/**
 * Rótulos e regras de tela das integrações (docs/planos/PLANO_CRIPTO_BINANCE.md, F6).
 * Um lugar só: a lista de conexões, a pré-visualização, o selo em Transações e
 * o marcador do menu falam a mesma língua.
 */

export type BadgeVariant = 'success' | 'danger' | 'warning' | 'info' | 'default';

/**
 * Nome de exibição da fonte, pelo catálogo que o servidor devolve (docs/planos/PLANO_INTEGRACOES.md, 10.1). Sem o
 * catálogo carregado, o código da fonte.
 */
export const providerName = (p: string | null | undefined, sources?: IntegrationSource[]) =>
  (p ? sources?.find(s => s.provider === p)?.name ?? p : '');

/**
 * Valor da proposta de importação na moeda dela: em reais como sempre; no ativo do exterior, na moeda dele (US$) — o
 * câmbio (PTAX venda da data) é aplicado ao gravar (docs/planos/PLANO_INTEGRACOES.md, D10). {@code maxDigits} > 2 para
 * preço unitário.
 */
export const formatProposalMoney = (value: number, currency: string | undefined, maxDigits = 2) =>
  !currency || currency === 'BRL'
    ? (maxDigits > 2 ? formatUnitPrice(value, true) : formatCurrency(value))
    : formatMoney(value, currency === 'USD' ? 'US$' : currency, 2, maxDigits);

/** O descritor da fonte, se o catálogo já veio. */
export const sourceOf = (p: string | null | undefined, sources?: IntegrationSource[]) =>
  sources?.find(s => s.provider === p);

export const CONNECTION_STATUS: Record<ConnectionStatus, { label: string; variant: BadgeVariant }> = {
  ACTIVE: { label: 'Ativa', variant: 'success' },
  PENDING_AUTHORIZATION: { label: 'Aguardando autorização', variant: 'warning' },
  INVALID_CREDENTIALS: { label: 'Credencial recusada', variant: 'danger' },
  PERMISSION_TOO_BROAD: { label: 'Chave com permissão demais', variant: 'danger' },
  REVOKED: { label: 'Desconectada', variant: 'default' },
};

export const RUN_STATUS: Record<RunStatus, { label: string; variant: BadgeVariant }> = {
  QUEUED: { label: 'Na fila', variant: 'info' },
  RUNNING: { label: 'Buscando…', variant: 'info' },
  AWAITING_CONFIRMATION: { label: 'Aguardando sua confirmação', variant: 'warning' },
  SUCCEEDED: { label: 'Concluída', variant: 'success' },
  FAILED: { label: 'Falhou', variant: 'danger' },
  CANCELLED: { label: 'Descartada', variant: 'default' },
};

export const RESOLUTION: Record<RunItemResolution, string> = {
  IMPORTED: 'Importada',
  REPLACED: 'Importada no lugar do lançamento manual',
  KEPT_BOTH: 'Importada; lançamento manual mantido',
  SKIPPED: 'Não importada',
};

export const KIND: Record<MovementKind, string> = {
  TRADE: 'Negociação',
  CONVERT: 'Convert',
  FIAT_DEPOSIT: 'Depósito em reais',
  CRYPTO_DEPOSIT: 'Depósito de cripto',
  CRYPTO_WITHDRAW: 'Saque de cripto',
  REWARD: 'Rendimento',
  EQUITY_TRADE: 'Negociação',
  SETTLEMENT: 'Liquidação',
  STOCK_EVENT: 'Evento do papel',
  CASH_EVENT: 'Provento',
  RIGHTS: 'Direito de subscrição',
  CUSTODY_TRANSFER: 'Transferência de custódia',
  LENDING: 'Empréstimo de ativos',
  FIXED_INCOME: 'Tesouro / renda fixa',
  FOREIGN_TRADE: 'Ação / ETF dos EUA',
  STABLECOIN_SETTLEMENT: 'Pagamento em stablecoin',
  TOKENIZED_CONVERT: 'Conversão em token',
  OTHER: 'Outro lançamento',
};

/** A busca ainda não terminou: a tela consulta de novo a cada poucos segundos. */
export const isRunInProgress = (s: RunStatus | null | undefined) => s === 'QUEUED' || s === 'RUNNING';

/** Precisa do usuário: pré-visualização aguardando, autorização pendente ou divergência com a corretora. */
export const needsAttention = (c: IntegrationConnection) =>
  c.status !== 'REVOKED' && (c.lastRunStatus === 'AWAITING_CONFIRMATION' || c.divergenceCount > 0
    || c.status === 'INVALID_CREDENTIALS' || c.status === 'PERMISSION_TOO_BROAD'
    || c.status === 'PENDING_AUTHORIZATION');

/** CPF com máscara enquanto digita: 52998224725 -> 529.982.247-25. */
export const formatCpf = (value: string): string => {
  const d = value.replace(/\D/g, '').slice(0, 11);
  return d.replace(/^(\d{3})(\d)/, '$1.$2').replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3')
    .replace(/\.(\d{3})(\d{1,2})$/, '.$1-$2');
};

/** Dígitos verificadores do CPF (a API confere de novo). */
export const isValidCpf = (value: string): boolean => {
  const d = value.replace(/\D/g, '');
  if (d.length !== 11 || /^(\d)\1{10}$/.test(d)) return false;
  const dv = (n: number) => {
    let sum = 0;
    for (let i = 0; i < n; i++) sum += Number(d[i]) * (n + 1 - i);
    const r = (sum * 10) % 11;
    return r === 10 ? 0 : r;
  };
  return dv(9) === Number(d[9]) && dv(10) === Number(d[10]);
};

/**
 * Data e hora no fuso de Brasília. As datas de execução vêm do servidor em UTC
 * sem "Z" (LocalDateTime); os instantes das operações vêm com "Z".
 */
export const formatDateTime = (value: string | null | undefined): string => {
  if (!value) return '—';
  const iso = /[zZ]|[+-]\d{2}:\d{2}$/.test(value) ? value : `${value}Z`;
  return new Intl.DateTimeFormat('pt-BR', {
    timeZone: 'America/Sao_Paulo', day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  }).format(new Date(iso));
};
