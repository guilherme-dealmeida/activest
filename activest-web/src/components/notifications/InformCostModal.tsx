import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { CurrencyInput } from '@/components/ui/CurrencyInput';
import { portfolioApi } from '@/api/portfolio';
import { useToast } from '@/context/ToastContext';
import { apiMessage } from '@/utils/apiError';

interface InformCostModalProps {
  isOpen: boolean;
  /** Da ação INFORMAR_CUSTO da mensagem (ou do ícone da Carteira): positionId, stockEventId, ticker, label. */
  params: Record<string, unknown>;
  onClose: () => void;
  onSaved: () => void;
}

/**
 * "Informar custo" da bonificação sem custo atribuído pela B3 (docs/planos/PLANO_NOTIFICACOES.md, 5.4 — PENDENCIAS
 * 13.2). O valor por ação está no fato relevante da empresa; vale só para a posição (não vai para o catálogo). Desde
 * 18/11/2026 é o custo para o IR: o preço médio da Carteira segue a corretora (bonificação sem custo) e o informado dá
 * o preço médio do IR (docs/planos/PLANO_EVENTOS_TRANSACOES.md, seção 8.4).
 */
export const InformCostModal = ({ isOpen, params, onClose, onSaved }: InformCostModalProps) => {
  const { showToast } = useToast();
  const initial = typeof params.costPerShare === 'number' ? params.costPerShare : null;
  const [cost, setCost] = useState<number | null>(initial);

  const save = useMutation({
    mutationFn: () => portfolioApi.setStockEventCost(String(params.positionId), Number(params.stockEventId), cost ?? 0),
    onSuccess: () => {
      showToast('Custo informado: o preço médio para o IR foi calculado.', 'success');
      onSaved();
    },
    onError: (e) => showToast(apiMessage(e) ?? 'Não foi possível gravar o custo.', 'error'),
  });

  const label = typeof params.label === 'string' ? params.label : 'Bonificação';
  const ticker = typeof params.ticker === 'string' ? params.ticker : '';

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={`Informar custo — ${ticker}`}
      footer={(
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>Cancelar</Button>
          <Button isLoading={save.isPending} disabled={cost === null || cost <= 0} onClick={() => save.mutate()}>
            Salvar
          </Button>
        </div>
      )}
    >
      <div className="space-y-4">
        <p className="text-sm text-content-2">
          {label}. As ações novas entram sem custo no preço médio, como na corretora. Para o imposto de renda, elas custam
          o valor atribuído pela empresa: informe o valor por ação que está no fato relevante — ele vale só para a sua
          posição e dá o preço médio para o IR (o preço médio da Carteira não muda).
        </p>
        <CurrencyInput
          label="Custo atribuído por ação"
          value={cost}
          decimals={8}
          onChange={setCost}
          autoFocus
        />
      </div>
    </Modal>
  );
};
