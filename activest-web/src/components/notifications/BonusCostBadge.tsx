import { useState } from 'react';
import { AlertTriangle } from 'lucide-react';
import { useQueryClient } from '@tanstack/react-query';
import { InformCostModal } from './InformCostModal';
import { PortfolioAssetDetail } from '@/types';

/**
 * Ícone de atenção ao lado do preço médio quando uma bonificação entrou sem custo atribuído pela B3
 * (docs/planos/PLANO_NOTIFICACOES.md, 5.4): o preço médio está com custo zero provisório. Clicar abre o "Informar
 * custo" — o mesmo da mensagem nas Notificações.
 */
export const BonusCostBadge = ({ asset }: { asset: PortfolioAssetDetail }) => {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const pending = asset.pendingBonusCosts ?? [];
  if (pending.length === 0) return null;
  const first = pending[0];
  const title = pending.length === 1
    ? `${first.label}: a B3 não informou o custo e as ${first.newShares} ações novas entraram com custo zero provisório. Clique para informar o custo por ação.`
    : `${pending.length} bonificações sem custo informado: o preço médio está com custo zero provisório. Clique para informar.`;

  return (
    <>
      <button
        type="button"
        onClick={(e) => { e.stopPropagation(); setOpen(true); }}
        className="text-yellow-500 hover:text-yellow-400"
        title={title}
        aria-label="Informar custo da bonificação"
      >
        <AlertTriangle className="h-3.5 w-3.5" />
      </button>
      {open && (
        <InformCostModal
          isOpen
          params={{ positionId: asset.id, stockEventId: first.stockEventId, ticker: asset.ticker, label: first.label }}
          onClose={() => setOpen(false)}
          onSaved={() => {
            setOpen(false);
            queryClient.invalidateQueries({ queryKey: ['portfolio'] });
            queryClient.invalidateQueries({ queryKey: ['notifications'] });
          }}
        />
      )}
    </>
  );
};
