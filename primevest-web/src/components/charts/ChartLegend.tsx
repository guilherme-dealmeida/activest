import { clsx } from 'clsx';

export interface ChartLegendItem {
  name: string;
  color: string;
  /** Valor já formatado (ex.: "34,66%" ou "R$ 1.000,00"). */
  value: string;
  /** Ideal da estratégia já formatado; quando algum item tem, a legenda ganha a coluna "ideal". */
  ideal?: string;
}

/**
 * Legenda dos gráficos de pizza/rosca (12/10/2026): um item abaixo do outro, em colunas alinhadas (cor, nome, valor
 * e, quando houver, o ideal da estratégia), com fonte um pouco menor que a do texto. Fica fora do gráfico, embaixo
 * dele: dentro do Recharts ela tomaria altura da rosca e tiraria o total do centro.
 */
export const ChartLegend = ({ items, className }: { items: ChartLegendItem[]; className?: string }) => {
  if (items.length === 0) return null;
  const hasIdeal = items.some(item => item.ideal !== undefined);

  return (
    <div className={clsx('flex justify-center', className)}>
      <ul
        className={clsx(
          'grid items-center gap-x-2.5 gap-y-1 text-[13px] leading-5',
          hasIdeal ? 'grid-cols-[auto_auto_auto_auto]' : 'grid-cols-[auto_auto_auto]'
        )}
      >
        {items.map(item => (
          <li key={item.name} className="contents">
            <span className="h-2.5 w-2.5 rounded-sm" style={{ backgroundColor: item.color }} aria-hidden />
            <span className="text-gray-300">{item.name}</span>
            <span className="text-white text-right tabular-nums">{item.value}</span>
            {hasIdeal && (
              <span className="text-gray-400 tabular-nums whitespace-nowrap">
                {item.ideal !== undefined ? `ideal ${item.ideal}` : ''}
              </span>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
};
