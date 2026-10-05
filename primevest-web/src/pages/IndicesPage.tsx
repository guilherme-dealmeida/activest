import { IndexChartCard } from '@/components/indices/IndexChartCard';
import { IndexCode } from '@/types';

/** Ordem dos cards definida em 05/10/2026. */
const ORDER: IndexCode[] = ['SELIC', 'CDI', 'IPCA', 'IBOV', 'IFIX', 'IGPM', 'INPC', 'TR', 'SELIC_META', 'USD_PTAX'];

/**
 * Tela Índices (docs/planos/PLANO_INDICES_MERCADO.md, adendo de 05/10/2026): um card por índice, um abaixo do
 * outro, cada um com o seu período e gráfico (o desenho da cotação das Emissoras).
 */
export const IndicesPage = () => (
  <div className="space-y-6">
    {ORDER.map(code => <IndexChartCard key={code} code={code} />)}
  </div>
);
