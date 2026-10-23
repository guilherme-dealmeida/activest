import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { transactionsApi } from '@/api/transactions';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { apiMessage } from '@/utils/apiError';
import { formatCurrency, formatDate } from '@/utils/format';
import { DistributorChangePreview, TransactionCreate, TransactionResponse } from '@/types';

interface Props {
  transactionId: string;
  preview: DistributorChangePreview;
  /** O formulário da compra editada, com a distribuidora nova. */
  purchase: TransactionCreate;
  /** Fechar sem gravar: volta ao formulário da compra. */
  onCancel: () => void;
  onDone: (saved: TransactionResponse | undefined) => void;
}

const shareCount = (v: number | null) =>
  v == null ? '—' : v.toLocaleString('pt-BR', { maximumFractionDigits: 2 });

/**
 * Troca de distribuidora de uma compra de Tesouro com venda ligada (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, 5.1).
 *
 * <p>A lista é a cascata: as vendas que consumiram a compra, as compras que essas vendas consumiram, e assim por
 * diante. A compra editada vem marcada e travada; as demais vêm marcadas (o caso comum é corrigir um erro em bloco).
 * Uma venda é um item só, mesmo repartida entre títulos. Gravar é tudo ou nada: se a conta não fecha numa das
 * distribuidoras, nada muda e o motivo aparece aqui, com o modal aberto para mudar a escolha.</p>
 */
export const DistributorChangeModal = ({ transactionId, preview, purchase, onCancel, onDone }: Props) => {
  const [selected, setSelected] = useState<Set<string>>(() => new Set(preview.items.map(i => i.id)));
  const [error, setError] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: () => transactionsApi.changeDistributor(
      transactionId,
      purchase,
      preview.items.filter(i => i.type === 'BUY' && !i.locked && selected.has(i.id)).map(i => i.id),
      preview.items.filter(i => i.type === 'SELL' && selected.has(i.id)).map(i => i.id),
    ),
    onSuccess: (saved) => onDone(saved),
    onError: (e: unknown) => setError(apiMessage(e) ?? 'Não foi possível trocar a distribuidora.'),
  });

  const toggle = (id: string) => {
    setError(null);
    setSelected(prev => {
      const n = new Set(prev);
      if (n.has(id)) n.delete(id); else n.add(id);
      return n;
    });
  };

  return (
    <Modal
      isOpen
      onClose={onCancel}
      title="Trocar distribuidora"
      size="lg"
      footer={
        <div className="flex justify-end gap-3">
          <Button type="button" variant="ghost" onClick={onCancel}>Voltar</Button>
          <Button type="button" isLoading={save.isPending} onClick={() => save.mutate()}>Salvar</Button>
        </div>
      }
    >
      <p className="text-sm text-gray-300">
        Quais transações abaixo quer mover de <span className="font-semibold text-white">{preview.fromName ?? '—'}</span>{' '}
        para <span className="font-semibold text-white">{preview.toName}</span>?
      </p>
      <p className="mt-1 text-xs text-gray-500">
        {preview.ticker}: as vendas que consumiram esta compra e as compras que essas vendas consumiram. O que ficar
        precisa fechar na distribuidora antiga, e o que for, na nova.
      </p>

      <div className="mt-4 divide-y divide-white/5 rounded-md border border-surface-light">
        {preview.items.map(item => (
          <label
            key={`${item.type}-${item.id}`}
            className={`flex items-center gap-3 px-3 py-2.5 text-sm ${item.locked ? 'opacity-70' : 'cursor-pointer hover:bg-white/5'}`}
          >
            <input
              type="checkbox"
              className="accent-accent"
              checked={item.locked || selected.has(item.id)}
              disabled={item.locked}
              onChange={() => toggle(item.id)}
            />
            <span className={`w-16 font-medium ${item.type === 'BUY' ? 'text-success' : 'text-danger'}`}>
              {item.type === 'BUY' ? 'Compra' : 'Venda'}
            </span>
            <span className="w-24 tabular-nums text-gray-300">{formatDate(item.date)}</span>
            <span className="flex-1 tabular-nums text-gray-300">
              {shareCount(item.shares)} cotas × {item.pricePerShare == null ? '—' : formatCurrency(item.pricePerShare)}
            </span>
            <span className="tabular-nums text-gray-200">{item.totalAmount == null ? '—' : formatCurrency(item.totalAmount)}</span>
            {item.locked && <span className="text-[10px] uppercase text-gray-500">esta compra</span>}
          </label>
        ))}
      </div>

      {error && (
        <div className="mt-4 rounded-md border border-danger/30 bg-danger/10 px-4 py-3 text-sm text-danger">{error}</div>
      )}
    </Modal>
  );
};
