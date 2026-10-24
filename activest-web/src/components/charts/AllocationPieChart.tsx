import type { ReactNode } from 'react';
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from 'recharts';
import { ChartLegend } from './ChartLegend';
import { legendItemsOf } from './legendItems';
import { CornerLegendDonut } from './CornerLegendDonut';
import { byValueDesc, DONUT_END_ANGLE, DONUT_START_ANGLE } from './donut';
import { formatCurrency, formatPercentage } from '@/utils/format';
import { LabelWithInfo } from '@/components/ui/InfoTooltip';

export interface AllocationData {
  name: string;
  value: number;
  percentage: number;
  color: string;
  /** % ideal da estratégia para a fatia; aparece na legenda quando informado. */
  ideal?: number;
}

interface AllocationPieChartProps {
  data: AllocationData[];
  totalValue: number;
  title?: string;
  /** Explicação no (i) ao lado do título. */
  titleInfo?: ReactNode;
  /**
   * Onde fica a legenda: embaixo (padrão) ou no canto inferior direito, com a rosca centralizada e subindo para o
   * lado dela só até onde não encosta (CornerLegendDonut; Dashboard, 12/10/2026).
   */
  legend?: 'bottom' | 'corner';
}

interface SliceTooltipProps {
  active?: boolean;
  payload?: { payload: AllocationData }[];
}

/** Balão de uma fatia (nome, valor, participação); usado também pelo PieOfPieChart. */
export const AllocationTooltip = ({ active, payload }: SliceTooltipProps) => {
  if (active && payload && payload.length) {
    const data = payload[0].payload;
    return (
      <div className="bg-surface border border-surface-light p-3 rounded-lg shadow-lg">
        <p className="font-medium text-white mb-1">{data.name}</p>
        <p className="text-sm text-gray-300">
          Valor: <span className="text-white font-medium">{formatCurrency(data.value)}</span>
        </p>
        <p className="text-sm text-gray-300">
          Participação: <span className="text-white font-medium">{formatPercentage(data.percentage)}</span>
        </p>
      </div>
    );
  }
  return null;
};

export const AllocationPieChart = ({ data: rawData, totalValue, title, titleInfo, legend = 'bottom' }: AllocationPieChartProps) => {
  // Regra das roscas (donut.ts): do maior para o menor, começando no topo.
  const data = byValueDesc(rawData);
  const totalCenter = (
    <>
      <span className="text-sm text-gray-400">Total</span>
      <span className="text-lg font-semibold text-white">{formatCurrency(totalValue)}</span>
    </>
  );

  if (legend === 'corner') {
    return (
      <div className="w-full flex flex-col items-center">
        {title && (
          <h3 className="text-lg font-medium text-white mb-4 text-center">
            <LabelWithInfo info={titleInfo}>{title}</LabelWithInfo>
          </h3>
        )}
        <CornerLegendDonut
          data={data}
          legendItems={legendItemsOf(data)}
          tooltip={<AllocationTooltip />}
          innerRatio={0.6}
          outerRatio={0.8}
          center={totalCenter}
        />
      </div>
    );
  }

  return (
    <div className="w-full h-full min-h-[300px] flex flex-col items-center">
      {title && (
        <h3 className="text-lg font-medium text-white mb-4 text-center">
          <LabelWithInfo info={titleInfo}>{title}</LabelWithInfo>
        </h3>
      )}
      {/* A rosca ocupa o que sobra; a legenda fica embaixo, fora do gráfico, e o total no centro exato. O
          ResponsiveContainer vai numa camada absoluta: "height: 100%" de um pai sem altura definida resolve para zero
          e a rosca some (aconteceu no Dashboard em 12/10/2026). Absoluta, ela tem o tamanho real da área. */}
      <div className="flex-1 w-full relative min-h-[240px]">
        <div className="absolute inset-0">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={data}
              cx="50%"
              cy="50%"
              innerRadius="60%"
              outerRadius="80%"
              startAngle={DONUT_START_ANGLE}
              endAngle={DONUT_END_ANGLE}
              paddingAngle={2}
              dataKey="value"
              stroke="none"
            >
              {data.map((entry, index) => (
                <Cell key={`cell-${index}`} fill={entry.color} />
              ))}
            </Pie>
            <Tooltip content={<AllocationTooltip />} />
          </PieChart>
        </ResponsiveContainer>
        </div>
        <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
          {totalCenter}
        </div>
      </div>
      <ChartLegend className="mt-3" items={legendItemsOf(data)} />
    </div>
  );
};
