import { useState } from 'react';
import { Info } from 'lucide-react';
import { useQueryClient } from '@tanstack/react-query';
import { InformCostModal } from './InformCostModal';
import { PortfolioAssetDetail } from '@/types';

/**
 * Ícone informativo ao lado do preço médio quando uma bonificação ainda não tem o custo atribuído
 * (docs/planos/PLANO_EVENTOS_TRANSACOES.md, D16). O preço médio da Carteira segue a corretora (bonificação sem custo);
 * o custo atribuído é o do IR. Clicar abre o "Informar custo" — o mesmo da mensagem nas Notificações.
 */
export const BonusCostBadge = ({ asset }: { asset: PortfolioAssetDetail }) => {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const pending = asset.pendingBonusCosts ?? [];
  if (pending.length === 0) return null;
  const first = pending[0];
  const title = pending.length === 1
    ? `${first.label}: as ${first.newShares} ações novas entraram sem custo, como na corretora. Para o IR, informe o custo atribuído por ação (clique).`
    : `${pending.length} bonificações sem o custo para o IR. Clique para informar.`;

  return (
    <>
      <button
        type="button"
        onClick={(e) => { e.stopPropagation(); setOpen(true); }}
        className="text-gray-400 hover:text-accent"
        title={title}
        aria-label="Informar custo da bonificação"
      >
        <Info className="h-3.5 w-3.5" />
      </button>
      {open && (
        <InformCostModal
          isOpen
          params={{ positionId: asset.id, stockEventId: first.stockEventId, ticker: asset.ticker, label: first.label }}
          onClose={() => setOpen(false)}
          onSaved={() => {
            setOpen(false);
            queryClient.invalidateQueries({ queryKey: ['portfolio'] });
            // a linha da bonificação no extrato (PLANO_EVENTOS_TRANSACOES.md)
            queryClient.invalidateQueries({ queryKey: ['transactions'] });
            queryClient.invalidateQueries({ queryKey: ['notifications'] });
          }}
        />
      )}
    </>
  );
};
