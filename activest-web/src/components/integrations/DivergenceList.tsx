import { AlertTriangle } from 'lucide-react';
import { Divergence } from '@/types';
import { formatQuantity } from '@/utils/format';

/**
 * Regra E3: ativos em que a posição no Activest difere do saldo Spot da
 * corretora no momento da busca.
 */
export const DivergenceList = ({ divergences, provider }: { divergences: Divergence[]; provider: string }) => {
  if (divergences.length === 0) return null;
  return (
    <div className="rounded-md border border-yellow-500/30 bg-yellow-500/10 p-4 text-sm">
      <p className="flex items-center gap-2 font-medium text-yellow-500">
        <AlertTriangle className="h-4 w-4" />
        {divergences.length === 1 ? '1 ativo diverge' : `${divergences.length} ativos divergem`} da {provider}
      </p>
      <p className="mt-1 text-gray-400">
        Comparação da carteira com o saldo Spot da {provider} no momento da busca. Operações que não são importadas
        (troca entre criptos, rendimentos, transferências) e o mesmo ativo em outra carteira ou corretora também
        aparecem aqui.
      </p>
      <table className="mt-3 w-full">
        <thead className="text-xs uppercase text-gray-400">
          <tr>
            <th className="py-1 text-left">Ativo</th>
            <th className="py-1 text-right">Activest</th>
            <th className="py-1 text-right">{provider}</th>
            <th className="py-1 text-right">Diferença</th>
          </tr>
        </thead>
        <tbody>
          {divergences.map(d => (
            <tr key={d.asset}>
              <td className="py-1 text-white font-medium">{d.asset}</td>
              <td className="py-1 text-right">{formatQuantity(d.activest, true)}</td>
              <td className="py-1 text-right">{formatQuantity(d.exchange, true)}</td>
              <td className={`py-1 text-right font-medium ${d.difference > 0 ? 'text-success' : 'text-danger'}`}>
                {d.difference > 0 ? '+' : ''}{formatQuantity(d.difference, true)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};
