import { useLayoutEffect, useRef, useState, type ReactElement, type ReactNode } from 'react';
import { PieChart, Pie, Cell, Tooltip } from 'recharts';
import { ChartLegend, type ChartLegendItem } from './ChartLegend';
import { byValueDesc, DONUT_END_ANGLE, DONUT_START_ANGLE } from './donut';

interface DonutSlice {
  name: string;
  value: number;
  color: string;
}

interface CornerLegendDonutProps<T extends DonutSlice> {
  data: T[];
  legendItems: ChartLegendItem[];
  /** Balão das fatias (elemento do Recharts, ex.: <AllocationTooltip />). */
  tooltip: ReactElement;
  /** Altura da área da rosca, em pixels. */
  height?: number;
  /** Raios como fração da metade do menor lado da área. */
  innerRatio?: number;
  outerRatio?: number;
  /** Conteúdo no centro da rosca (ex.: o total). */
  center?: ReactNode;
}

const LEGEND_GAP = 12;

/**
 * Rosca centralizada com a legenda no canto inferior direito (12/10/2026; aba Por Categoria da Alocação e
 * Dashboard). A legenda sobe para o lado da rosca só até onde não encosta nela — o ponto sai da geometria (largura da
 * área, raio, largura e altura da legenda); onde ela cairia em cima da rosca (área estreita, legenda larga), fica
 * inteira embaixo. A largura é lida já na montagem: o ResizeObserver só avisa no ciclo seguinte do navegador.
 */
export const CornerLegendDonut = <T extends DonutSlice>({
  data: rawData,
  legendItems: rawLegendItems,
  tooltip,
  height = 320,
  innerRatio = 0.45,
  outerRatio = 0.85,
  center,
}: CornerLegendDonutProps<T>) => {
  // Regra das roscas (donut.ts): fatias do maior para o menor, a partir do topo, e a legenda na mesma ordem.
  const data = byValueDesc(rawData);
  const order = new Map(data.map((d, i) => [d.name, i]));
  const legendItems = [...rawLegendItems].sort(
    (a, b) => (order.get(a.name) ?? Number.MAX_SAFE_INTEGER) - (order.get(b.name) ?? Number.MAX_SAFE_INTEGER)
  );
  const boxRef = useRef<HTMLDivElement>(null);
  const legendRef = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(0);
  const [pull, setPull] = useState(0);

  useLayoutEffect(() => {
    const box = boxRef.current;
    const legend = legendRef.current;
    if (!box || !legend) return;
    const measure = () => {
      const w = box.clientWidth;
      const legendWidth = legend.offsetWidth;
      const legendHeight = legend.offsetHeight;
      const radius = (outerRatio * Math.min(w, height)) / 2;
      const middle = height / 2;
      // Distância horizontal da borda esquerda da legenda ao centro da rosca.
      const dx = w - legendWidth - w / 2;
      let minTop: number;
      if (dx >= radius) {
        minTop = middle; // ao lado da rosca, sem encostar: no máximo até a altura do centro
      } else if (dx > 0) {
        minTop = middle + Math.sqrt(radius * radius - dx * dx) + LEGEND_GAP; // abaixo da curva naquele x
      } else {
        minTop = height + LEGEND_GAP; // passaria por baixo do centro: vai inteira para baixo
      }
      // Sobe no máximo a própria altura: a base da legenda nunca fica acima da base da área da rosca.
      setWidth(w);
      setPull(Math.min(height - minTop, legendHeight));
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(box);
    observer.observe(legend);
    return () => observer.disconnect();
    // A lista chega nova a cada render; o que muda o tamanho da legenda é a quantidade de itens (e o observer).
  }, [legendItems.length, height, outerRatio]);

  return (
    <div ref={boxRef} className="w-full">
      <div className="relative w-full" style={{ height }}>
        {width > 0 && (
          <PieChart width={width} height={height} margin={{ top: 0, right: 0, bottom: 0, left: 0 }}>
            <Pie
              data={data}
              cx="50%"
              cy="50%"
              innerRadius={`${innerRatio * 100}%`}
              outerRadius={`${outerRatio * 100}%`}
              startAngle={DONUT_START_ANGLE}
              endAngle={DONUT_END_ANGLE}
              paddingAngle={data.length > 1 ? 2 : 0}
              dataKey="value"
              stroke="none"
            >
              {data.map((entry, index) => (
                <Cell key={`cell-${index}`} fill={entry.color} />
              ))}
            </Pie>
            <Tooltip content={tooltip} />
          </PieChart>
        )}
        {center && (
          <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">{center}</div>
        )}
      </div>
      <div ref={legendRef} className="relative ml-auto w-fit" style={{ marginTop: -pull }}>
        <ChartLegend items={legendItems} />
      </div>
    </div>
  );
};
