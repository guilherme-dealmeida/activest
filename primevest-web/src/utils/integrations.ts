import { ConnectionStatus, IntegrationConnection, MovementKind, RunItemResolution, RunStatus } from '@/types';

/**
 * Rótulos e regras de tela das integrações (PLANO_CRIPTO_BINANCE.md, F6).
 * Um lugar só: a lista de conexões, a pré-visualização, o selo em Transações e
 * o marcador do menu falam a mesma língua.
 */

export type BadgeVariant = 'success' | 'danger' | 'warning' | 'info' | 'default';

export const PROVIDER_NAME: Record<string, string> = { BINANCE: 'Binance' };

export const providerName = (p: string | null | undefined) => (p ? PROVIDER_NAME[p] ?? p : '');

export const CONNECTION_STATUS: Record<ConnectionStatus, { label: string; variant: BadgeVariant }> = {
  ACTIVE: { label: 'Ativa', variant: 'success' },
  INVALID_CREDENTIALS: { label: 'Chave recusada', variant: 'danger' },
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
};

/** A busca ainda não terminou: a tela consulta de novo a cada poucos segundos. */
export const isRunInProgress = (s: RunStatus | null | undefined) => s === 'QUEUED' || s === 'RUNNING';

/** Precisa do usuário: pré-visualização aguardando ou divergência com a corretora. */
export const needsAttention = (c: IntegrationConnection) =>
  c.status !== 'REVOKED' && (c.lastRunStatus === 'AWAITING_CONFIRMATION' || c.divergenceCount > 0
    || c.status === 'INVALID_CREDENTIALS' || c.status === 'PERMISSION_TOO_BROAD');

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
