import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from 'recharts';
import { formatCurrency, formatPercentage } from '@/utils/format';
import { PortfolioAllocationDetailed } from '@/types';
import { Card } from '../ui/Card';

interface AllocationDetailedChartsProps {
  data: PortfolioAllocationDetailed[];
}

const hexToHsl = (hex: string): [number, number, number] => {
  let h = hex.replace('#', '').trim();
  if (h.length === 3) h = h.split('').map(c => c + c).join('');
  const r = parseInt(h.substring(0, 2), 16) / 255;
  const g = parseInt(h.substring(2, 4), 16) / 255;
  const b = parseInt(h.substring(4, 6), 16) / 255;
  if ([r, g, b].some(Number.isNaN)) return [0, 0, 50];

  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const l = (max + min) / 2;
  let hue = 0;
  let sat = 0;

  if (max !== min) {
    const d = max - min;
    sat = l > 0.5 ? d / (2 - max - min) : d / (max + min);
    switch (max) {
      case r: hue = (g - b) / d + (g < b ? 6 : 0); break;
      case g: hue = (b - r) / d + 2; break;
      default: hue = (r - g) / d + 4;
    }
    hue *= 60;
  }
  return [hue, sat * 100, l * 100];
};

const hslToHex = (h: number, s: number, l: number): string => {
  const sat = s / 100;
  const lig = l / 100;
  const k = (n: number) => (n + h / 30) % 12;
  const a = sat * Math.min(lig, 1 - lig);
  const f = (n: number) => {
    const c = lig - a * Math.max(-1, Math.min(k(n) - 3, Math.min(9 - k(n), 1)));
    return Math.round(c * 255).toString(16).padStart(2, '0');
  };
  return `#${f(0)}${f(8)}${f(4)}`;
};

/**
 * Gera um degradê da cor do tipo de ativo: do tom mais claro ao mais escuro,
 * centrado na cor base. Os limites de luminosidade evitam tons que somem no
 * fundo escuro ou viram quase branco.
 */
const generateGradient = (baseColor: string, count: number): string[] => {
  if (count <= 1) return [baseColor];
  const [h, s, l] = hexToHsl(baseColor);
  const spread = Math.min(40, 12 * (count - 1));
  const lightest = Math.min(l + spread / 2, 85);
  const darkest = Math.max(l - spread / 2, 25);
  return Array.from({ length: count }, (_, i) => {
    const t = i / (count - 1);
    return hslToHex(h, s, lightest + (darkest - lightest) * t);
  });
};

// Fatia do gráfico de uma categoria, como vem no payload do Recharts.
interface AllocationSlice {
  name: string;
  value: number;
  percentage: number;
}

interface SliceTooltipProps {
  active?: boolean;
  payload?: { payload: AllocationSlice }[];
}

const CustomTooltip = ({ active, payload }: SliceTooltipProps) => {
  if (active && payload && payload.length) {
    const data = payload[0].payload;
    return (
      <div className="bg-surface border border-surface-light p-3 rounded-lg shadow-lg z-50">
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

export const AllocationDetailedCharts = ({ data }: AllocationDetailedChartsProps) => {
  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
      {data.map((typeItem, typeIdx) => {
        if (typeItem.totalValue <= 0) return null;

        // Maior participação primeiro: o degradê acompanha o peso de cada categoria.
        const sorted = [...typeItem.categories].sort((a, b) => b.value - a.value);
        const colors = generateGradient(typeItem.colorHex, sorted.length);

        const chartData = sorted.map((cat, idx) => ({
          name: cat.categoryName,
          value: cat.value,
          percentage: cat.percentage,
          color: colors[idx]
        }));

        return (
          <Card key={typeIdx} title={typeItem.typeName} className="flex flex-col items-center">
            <div className="w-full h-64 relative">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={chartData}
                    cx="50%"
                    cy="50%"
                    innerRadius="45%"
                    outerRadius="85%"
                    paddingAngle={chartData.length > 1 ? 2 : 0}
                    dataKey="value"
                    stroke="none"
                  >
                    {chartData.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={entry.color} />
                    ))}
                  </Pie>
                  <Tooltip content={<CustomTooltip />} />
                </PieChart>
              </ResponsiveContainer>
            </div>
            <ul className="w-full mt-4 text-sm space-y-1.5">
              {chartData.map((item, idx) => (
                <li key={idx} className="flex items-center justify-between gap-3">
                  <div className="flex items-center gap-2 min-w-0">
                    <span className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ backgroundColor: item.color }} />
                    <span className="text-gray-300 truncate" title={item.name}>{item.name}</span>
                  </div>
                  <span className="font-medium text-white flex-shrink-0 tabular-nums">{formatPercentage(item.percentage)}</span>
                </li>
              ))}
            </ul>
          </Card>
        );
      })}
    </div>
  );
};
