import { CircleDollarSign, List } from 'lucide-react';
import { AssetLogo } from '@/components/ui/AssetLogo';
import { TickerLink } from '@/components/issuer/TickerLink';
import { TABLE_ACTION_CLASS } from '@/components/ui/tableAction';
import { formatCurrency, formatDate, formatShares } from '@/utils/format';
import {
  fractionHint, signedShares, stockEventBadge, stockEventTitle, StockEventRow, taxCostHint, taxCostLabel,
} from '@/utils/stockEventRows';

interface Props {
  row: StockEventRow;
  /** Nome técnico do tipo de ativo (logo por tipo). */
  typeName: string | null;
  onList: (row: StockEventRow) => void;
  onInformCost: (row: StockEventRow) => void;
}

/**
 * Linha de um evento do papel (bonificação, desdobramento, grupamento) no extrato de Transações
 * (docs/planos/PLANO_EVENTOS_TRANSACOES.md). Não é transação: vem do catálogo e entra na posição pela mesma conta da
 * Carteira, então não tem Editar, Excluir, Comprar nem Vender (D6). A bonificação entra sem custo, como na corretora
 * (seção 8.4 do plano): preço e total R$ 0,00, com o custo do IR embaixo (D15) e o "Informar custo".
 */
export const StockEventLine = ({ row, typeName, onList, onInformCost }: Props) => {
  const e = row.event;
  const bonus = e.type === 'BONIFICACAO';
  const taxLabel = taxCostLabel(e);
  const taxHint = taxCostHint(e);
  const what = e.source === 'MASSIVE' ? 'do catálogo dos EUA (Massive)' : 'do catálogo da B3';
  return (
    <tr className="hover:bg-overlay transition-colors">
      <td className="px-4 py-3 text-content font-medium">
        <div className="flex items-center gap-3" title={row.assetName || undefined}>
          {row.hasLogo !== null && row.hasLogo !== undefined && (
            <AssetLogo ticker={row.ticker} hasLogo={row.hasLogo} assetType={typeName} size={32} shape="square" />
          )}
          <div className="flex flex-wrap items-center gap-y-1">
            <TickerLink ticker={row.ticker} issuerId={row.issuerId} />
            <span
              className="ml-2 whitespace-nowrap rounded bg-info/15 px-1.5 py-0.5 text-[0.625rem] font-normal text-info"
              title={`Evento do papel ${what}: entra na posição pela mesma conta da Carteira. Não é uma transação — `
                + 'não se edita nem se exclui.'}
            >
              {stockEventBadge(e.source)}
            </span>
          </div>
        </div>
      </td>
      <td className="px-4 py-3 text-content/70">—</td>
      <td className="px-4 py-3">
        <div>{formatDate(e.exDate)}</div>
        <div className="text-xs text-content-3">data-com {formatDate(e.dataCom)}</div>
      </td>
      <td className="px-4 py-3 text-content-3">{row.assetType}</td>
      <td className="px-4 py-3">
        <span className="font-medium text-accent-text whitespace-nowrap">{stockEventTitle(e)}</span>
      </td>
      <td className="px-4 py-3 text-right" title={fractionHint(e)}>
        <span className="inline-flex flex-col items-end">
          <span className="tabular-nums">{signedShares(e.sharesDelta)}</span>
          <span className="text-xs text-content-3 tabular-nums">
            {formatShares(e.sharesBefore)} → {formatShares(e.sharesAfter)}
            {e.fractionShares > 0 && ' *'}
          </span>
        </span>
      </td>
      <td className="px-4 py-3 text-right">
        {bonus ? (
          <span className="inline-flex flex-col items-end" title={taxHint}>
            <span>{formatCurrency(0)}</span>
            {taxLabel && <span className="text-xs text-content-3 whitespace-nowrap">{taxLabel}</span>}
          </span>
        ) : '—'}
      </td>
      <td className="px-4 py-3 text-right font-medium text-content">
        {bonus ? (
          <span className="inline-flex flex-col items-end" title={taxHint}>
            <span>{formatCurrency(e.costDelta)}</span>
            {e.taxCostDelta != null && (
              <span className="text-xs font-normal text-content-3 whitespace-nowrap">IR: {formatCurrency(e.taxCostDelta)}</span>
            )}
          </span>
        ) : '—'}
      </td>
      <td className="px-4 py-3 text-center">
        <div className="flex items-center justify-center gap-1">
          <button onClick={() => onList(row)} className={TABLE_ACTION_CLASS} title="Ver as transações desta posição">
            <List className="w-4 h-4" />
          </button>
          {bonus && e.taxCostOrigin !== 'CATALOG' && e.stockEventId != null && (
            <button
              onClick={() => onInformCost(row)}
              className={TABLE_ACTION_CLASS}
              title={e.taxCostOrigin === 'USER'
                ? 'Alterar o custo da bonificação para o IR' : 'Informar o custo da bonificação para o IR'}
              aria-label="Informar custo da bonificação"
            >
              <CircleDollarSign className="w-4 h-4" />
            </button>
          )}
        </div>
      </td>
    </tr>
  );
};
