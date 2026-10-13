import { useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { PieChart, Pie, Cell, Tooltip } from 'recharts';
import { formatCurrency } from '@/utils/format';
import { LabelWithInfo } from '@/components/ui/InfoTooltip';
import { AllocationPieChart, AllocationTooltip, type AllocationData } from './AllocationPieChart';
import { legendItemsOf } from './legendItems';
import { ChartLegend } from './ChartLegend';
import { byValueDesc, DONUT_END_ANGLE, DONUT_START_ANGLE } from './donut';

interface PieSide {
  data: AllocationData[];
  total: number;
  title: string;
  /** Explicação no (i) ao lado do título. */
  titleInfo?: ReactNode;
}

interface PieOfPieChartProps {
  /** Pizza principal (à esquerda). */
  main: PieSide;
  /** Pizza de detalhe (à direita): a fatia `expandedName` da principal, aberta. */
  detail: PieSide;
  expandedName: string;
}

/** Abaixo desta largura as pizzas empilham, sem as linhas de ligação (decisão 4, 12/10/2026). */
const MIN_WIDTH = 760;
const CHART_HEIGHT = 440;
/** A pizza de detalhe tem 75% do tamanho da principal, como no Excel (decisão 2). */
const DETAIL_RATIO = 0.75;
/** Rosca (decisão 1): mesma proporção do AllocationPieChart, 60% / 80%. */
const INNER_RATIO = 0.75;
const PADDING_ANGLE = 2;
/** Centros: 27% e 77% da largura, o meio das colunas de 54% e 46% dos títulos e das legendas. */
const MAIN_CX = 0.27;
const DETAIL_CX = 0.77;

const toRad = (deg: number) => (deg * Math.PI) / 180;

const CenterTotal = ({ x, y, total }: { x: number; y: number; total: number }) => (
  <div
    className="absolute flex flex-col items-center pointer-events-none -translate-x-1/2 -translate-y-1/2"
    style={{ left: x, top: y }}
  >
    <span className="text-sm text-gray-400">Total</span>
    <span className="text-lg font-semibold text-white whitespace-nowrap">{formatCurrency(total)}</span>
  </div>
);

/**
 * "Pizza de pizza" (12/10/2026, tela Alocação): a pizza principal com a fatia `expandedName` virada para a direita,
 * e essa fatia aberta numa segunda pizza menor, ligadas por duas linhas — o gráfico do Excel. Duas roscas num só
 * PieChart, com a geometria calculada em pixels a partir da largura medida; título, total no centro e legenda de
 * cada pizza ficam embaixo/acima dela. Em tela estreita volta a ser duas AllocationPieChart empilhadas.
 */
export const PieOfPieChart = ({ main, detail, expandedName }: PieOfPieChartProps) => {
  const ref = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(0);

  // Largura lida já na montagem, antes do primeiro desenho: o ResizeObserver só avisa no próximo ciclo de
  // renderização do navegador (que nem acontece com a aba escondida), e até lá o gráfico ficaria em branco.
  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    setWidth(el.getBoundingClientRect().width);
    const observer = new ResizeObserver(entries => setWidth(entries[0].contentRect.width));
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  // O invólucro medido é sempre o mesmo elemento: trocar de elemento ao empilhar deixaria o observer olhando um nó
  // desmontado, e a largura pararia de ser atualizada.
  if (width > 0 && width < MIN_WIDTH) {
    return (
      <div ref={ref} className="w-full">
        <div className="grid grid-cols-1 gap-6">
          {[main, detail].map(side => (
            <div key={side.title} className="h-[560px]">
              <AllocationPieChart data={side.data} totalValue={side.total} title={side.title} titleInfo={side.titleInfo} />
            </div>
          ))}
        </div>
      </div>
    );
  }

  // Geometria, em pixels.
  const cy = CHART_HEIGHT / 2;
  const mainCx = width * MAIN_CX;
  const detailCx = width * DETAIL_CX;
  const outer = Math.max(0, Math.min(CHART_HEIGHT / 2 - 16, width * 0.2));
  const detailOuter = outer * DETAIL_RATIO;

  // A fatia aberta vai primeiro e fica centrada em 0° (virada para a direita, de frente para a segunda pizza). O
  // Recharts desconta o espaçamento das fatias do giro: o meio-ângulo dela é (360 − espaçamento) × fração ÷ 2.
  // Regra das roscas (donut.ts): a de detalhe vai do maior para o menor a partir do topo. A principal é a exceção —
  // girada para a fatia aberta ficar de frente para a de detalhe —, mas a legenda dela segue a ordem decrescente.
  const detailData = byValueDesc(detail.data);
  const expanded = main.data.find(d => d.name === expandedName);
  const ordered = expanded ? [expanded, ...main.data.filter(d => d !== expanded)] : main.data;
  const mainSum = main.data.reduce((acc, d) => acc + d.value, 0);
  const fraction = expanded && mainSum > 0 ? expanded.value / mainSum : 0;
  const padding = ordered.length > 1 ? PADDING_ANGLE : 0;
  const halfAngle = ((360 - padding * ordered.length) * fraction) / 2;
  const startAngle = halfAngle;
  const endAngle = startAngle - 360;

  // Linhas: das bordas da fatia aberta ao topo e à base da segunda pizza. Fatia maior que meia pizza teria a borda
  // do lado esquerdo e a linha cruzaria a rosca: aí a linha sai do topo e da base da principal, como no Excel.
  const edge = toRad(Math.min(halfAngle, 90));
  const showLines = width > 0 && expanded !== undefined && detail.data.length > 0;
  const lineStartX = mainCx + outer * Math.cos(edge);
  const lineDy = outer * Math.sin(edge);

  return (
    <div ref={ref} className="w-full">
      {width > 0 && (
        <>
          <div className="grid grid-cols-[54%_46%]">
            {[main, detail].map(side => (
              <h3 key={side.title} className="text-lg font-medium text-white text-center px-2">
                <LabelWithInfo info={side.titleInfo}>{side.title}</LabelWithInfo>
              </h3>
            ))}
          </div>

          {/* Camada do gráfico em posição absoluta: com largura fixa em pixels ela entraria na largura mínima do
              layout, que então não encolheria mais ao estreitar a janela (o ResizeObserver nunca veria a redução). */}
          <div className="relative overflow-hidden" style={{ height: CHART_HEIGHT }}>
            <div className="absolute inset-0">
            {/* Margem zero: a padrão do Recharts (5 px) desloca o centro das pizzas, e as linhas e os totais, que
                são desenhados por fora com a mesma geometria, ficavam 5 px fora da rosca. */}
            <PieChart width={width} height={CHART_HEIGHT} margin={{ top: 0, right: 0, bottom: 0, left: 0 }}>
              <Pie
                data={ordered}
                dataKey="value"
                cx={mainCx}
                cy={cy}
                innerRadius={outer * INNER_RATIO}
                outerRadius={outer}
                startAngle={startAngle}
                endAngle={endAngle}
                paddingAngle={padding}
                stroke="none"
              >
                {ordered.map(entry => <Cell key={entry.name} fill={entry.color} />)}
              </Pie>
              <Pie
                data={detailData}
                dataKey="value"
                cx={detailCx}
                cy={cy}
                innerRadius={detailOuter * INNER_RATIO}
                outerRadius={detailOuter}
                startAngle={DONUT_START_ANGLE}
                endAngle={DONUT_END_ANGLE}
                paddingAngle={detailData.length > 1 ? PADDING_ANGLE : 0}
                stroke="none"
              >
                {detailData.map(entry => <Cell key={entry.name} fill={entry.color} />)}
              </Pie>
              <Tooltip content={<AllocationTooltip />} />
            </PieChart>

            {showLines && (
              <svg className="absolute inset-0 pointer-events-none" width={width} height={CHART_HEIGHT} aria-hidden>
                <g stroke={expanded?.color} strokeWidth={1.5} strokeOpacity={0.8}>
                  <line x1={lineStartX} y1={cy - lineDy} x2={detailCx} y2={cy - detailOuter} />
                  <line x1={lineStartX} y1={cy + lineDy} x2={detailCx} y2={cy + detailOuter} />
                </g>
              </svg>
            )}

            <CenterTotal x={mainCx} y={cy} total={main.total} />
            <CenterTotal x={detailCx} y={cy} total={detail.total} />
            </div>
          </div>

          <div className="grid grid-cols-[54%_46%] items-start">
            <ChartLegend className="mt-2" items={legendItemsOf(byValueDesc(main.data))} />
            <ChartLegend className="mt-2" items={legendItemsOf(detailData)} />
          </div>
        </>
      )}
    </div>
  );
};
