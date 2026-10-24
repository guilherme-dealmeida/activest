import { IndicatorPoint } from '@/types';

interface Props {
  points: IndicatorPoint[];
  /** Descrição para leitor de tela. */
  label: string;
  width?: number;
  height?: number;
}

const GOLD = '#c9a84c';

/**
 * Tendência de um indicador no rodapé do cartão (docs/planos/PLANO_TELA_EMISSORES.md, 2.4):
 * pontos anuais + Atual, sem eixo. Nulo interrompe a linha; zero é ponto no
 * zero; o último ponto (Atual) ganha marcador dourado. SVG puro — dezenas de
 * cartões na tela não justificam um gráfico completo em cada um.
 */
export const Sparkline = ({ points, label, width = 160, height = 36 }: Props) => {
  const values = points.map(p => p.value).filter((v): v is number => v != null);
  if (values.length < 2) {
    return <div style={{ height }} className="flex items-end text-[11px] text-gray-500">sem série suficiente</div>;
  }
  const min = Math.min(...values);
  const max = Math.max(...values);
  const span = max - min || Math.abs(max) || 1;
  const pad = 4;
  const x = (i: number) => pad + (i * (width - 2 * pad)) / Math.max(points.length - 1, 1);
  const y = (v: number) => pad + (height - 2 * pad) * (1 - (v - min) / span);

  // um caminho por trecho contínuo (nulo quebra a linha)
  const segments: string[] = [];
  let current = '';
  points.forEach((p, i) => {
    if (p.value == null) {
      if (current) segments.push(current);
      current = '';
      return;
    }
    current += `${current ? 'L' : 'M'}${x(i).toFixed(1)},${y(p.value).toFixed(1)}`;
  });
  if (current) segments.push(current);

  const lastIdx = points.length - 1;
  const last = points[lastIdx];
  const zeroY = min < 0 && max > 0 ? y(0) : null;

  return (
    <svg width="100%" height={height} viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none"
         role="img" aria-label={label} className="overflow-visible">
      {zeroY != null && (
        <line x1={0} x2={width} y1={zeroY} y2={zeroY} stroke="currentColor" strokeOpacity={0.2}
              strokeDasharray="2 2" vectorEffect="non-scaling-stroke" />
      )}
      {segments.map((d, i) => (
        <path key={i} d={d} fill="none" stroke={GOLD} strokeOpacity={0.75} strokeWidth={1.5}
              strokeLinejoin="round" strokeLinecap="round" vectorEffect="non-scaling-stroke" />
      ))}
      {last.value != null && (
        // ponto de comprimento zero com ponta redonda: continua redondo com preserveAspectRatio="none"
        <path d={`M${x(lastIdx).toFixed(1)},${y(last.value).toFixed(1)}h0`} stroke={GOLD} strokeWidth={6}
              strokeLinecap="round" vectorEffect="non-scaling-stroke" />
      )}
    </svg>
  );
};
