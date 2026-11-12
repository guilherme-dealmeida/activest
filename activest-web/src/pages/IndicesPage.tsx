import { IndexChartCard } from '@/components/indices/IndexChartCard';
import { YieldCurveCard } from '@/components/indices/YieldCurveCard';
import { IndexCode } from '@/types';

/** Ordem dos cards definida em 05/10/2026; a curva de juros do Tesouro logo abaixo da meta Selic (11/11/2026, D8). */
const BEFORE_CURVES: IndexCode[] = ['SELIC', 'CDI', 'IPCA', 'IBOV', 'IFIX', 'IGPM', 'INPC', 'TR', 'SELIC_META'];
const AFTER_CURVES: IndexCode[] = ['USD_PTAX'];

/**
 * Tela Índices (docs/planos/PLANO_INDICES_MERCADO.md, adendo de 05/10/2026): um card por índice, um abaixo do
 * outro, cada um com o seu período e gráfico (o desenho da cotação de Ativos). As curvas de juros do Tesouro
 * (docs/planos/PLANO_JUROS_FUTUROS.md) têm um card por curva, com seletor de prazo e a curva do dia.
 */
export const IndicesPage = () => (
  <div className="space-y-6">
    {BEFORE_CURVES.map(code => <IndexChartCard key={code} code={code} />)}
    <YieldCurveCard curve="PRE" />
    <YieldCurveCard curve="REAL" />
    {AFTER_CURVES.map(code => <IndexChartCard key={code} code={code} />)}
  </div>
);
