import { Card } from '@/components/ui/Card';
import { PerformanceSeries } from '@/types/performance';
import { CumulativeChart } from './PerformanceCharts';

const br = (iso: string) => iso.split('-').reverse().join('/');

/**
 * Card "Rentabilidade acumulada": gráfico da carteira e dos indicadores e o período disponível. Usado na tela
 * Rentabilidade (com os filtros dela) e no Dashboard (17/10/2026, recorte fixo: carteira total com reserva, bruto,
 * 12 meses, CDI).
 */
export const CumulativeCard = ({ data, className }: { data?: PerformanceSeries; className?: string }) => {
  const cov = data?.coverage;
  return (
    <Card title="Rentabilidade acumulada" className={className}
      titleInfo="Evolução da rentabilidade da carteira (TWR) e dos indicadores escolhidos no mesmo período, a partir de zero no dia-base. Clique na legenda para esconder uma série.">
      {data && <CumulativeChart data={data} />}
      {cov && <p className="mt-2 text-xs text-gray-500">Período disponível: {cov.from && cov.to ? `${br(cov.from)} a ${br(cov.to)}` : '—'}
        {cov.firstDate && ` · carteira desde ${br(cov.firstDate)}`}</p>}
    </Card>
  );
};
