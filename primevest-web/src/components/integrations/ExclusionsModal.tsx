import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { RotateCcw } from 'lucide-react';
import { Modal } from '@/components/ui/Modal';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { integrationsApi } from '@/api/integrations';
import { useToast } from '@/context/ToastContext';
import { apiMessage } from '@/utils/apiError';
import { formatCurrency, formatQuantity } from '@/utils/format';
import { formatDateTime } from '@/utils/integrations';
import { IntegrationConnection } from '@/types';

interface Props {
  connection: IntegrationConnection | null;
  onClose: () => void;
}

/**
 * Operações importadas que o usuário excluiu (regra E2). Restaurar traz a
 * operação de volta para a carteira na hora, com os valores da corretora (E4).
 */
export const ExclusionsModal = ({ connection, onClose }: Props) => {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const id = connection?.id ?? '';

  const { data, isLoading } = useQuery({
    queryKey: ['integrations', id, 'exclusions'],
    queryFn: () => integrationsApi.exclusions(id),
    enabled: !!connection,
  });

  const restore = useMutation({
    mutationFn: (exclusionId: string) => integrationsApi.restoreExclusion(id, exclusionId),
    onSuccess: (tx) => {
      queryClient.invalidateQueries({ queryKey: ['integrations'] });
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
      queryClient.invalidateQueries({ queryKey: ['portfolio'] });
      showToast(`Operação de ${tx.ticker} restaurada na carteira.`, 'success');
    },
    onError: (e: unknown) => showToast(apiMessage(e) ?? 'Não foi possível restaurar.', 'error', 8000),
  });

  return (
    <Modal isOpen={!!connection} onClose={onClose} title={`Operações excluídas · ${connection?.label ?? ''}`} size="lg"
      titleInfo="Operações importadas que você excluiu. Elas não voltam nas próximas sincronizações; restaurar devolve a operação à carteira na hora.">
      {isLoading ? (
        <div className="flex justify-center py-8"><LoadingSpinner className="h-6 w-6 text-accent" /></div>
      ) : !data || data.length === 0 ? (
        <p className="py-6 text-center text-sm text-gray-500">Nenhuma operação excluída.</p>
      ) : (
        <table className="w-full text-sm">
          <thead className="text-xs uppercase text-gray-400 border-b border-white/10">
            <tr>
              <th className="py-2 text-left">Data</th>
              <th className="py-2 text-left">Ativo</th>
              <th className="py-2 text-right">Quantidade</th>
              <th className="py-2 text-right">Valor</th>
              <th className="py-2 text-right">Excluída em</th>
              <th className="py-2" />
            </tr>
          </thead>
          <tbody className="divide-y divide-white/5">
            {data.map(e => (
              <tr key={e.id}>
                <td className="py-2">{formatDateTime(e.executedAt)}</td>
                <td className="py-2 text-white font-medium">
                  {e.ticker} <span className="text-gray-400 font-normal">{e.side === 'SELL' ? 'venda' : 'compra'}</span>
                </td>
                <td className="py-2 text-right">{e.grossQuantity != null ? formatQuantity(e.grossQuantity, true) : '—'}</td>
                <td className="py-2 text-right">{e.quoteAmount != null ? formatCurrency(e.quoteAmount) : '—'}</td>
                <td className="py-2 text-right text-gray-400">{formatDateTime(e.excludedAt)}</td>
                <td className="py-2 text-right">
                  <button onClick={() => restore.mutate(e.id)} disabled={restore.isPending}
                    className="inline-flex items-center gap-1 text-xs text-accent hover:underline disabled:opacity-50">
                    <RotateCcw className="h-3.5 w-3.5" /> Restaurar
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Modal>
  );
};
