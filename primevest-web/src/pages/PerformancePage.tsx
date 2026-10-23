import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { assetsApi } from '@/api/assets';
import { performanceApi } from '@/api/performance';
import { Card } from '@/components/ui/Card';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { apiMessage } from '@/utils/apiError';
import {
  BreakdownLevel, BreakdownRow, Granularity, PerformanceBasis, PerformanceQuery, PerformanceRange, ReservationFilter,
} from '@/types/performance';
import { PerformanceFilters } from '@/components/performance/PerformanceFilters';
import { BenchmarkPick, MAX_BENCHMARKS, RANGES, benchKey, benchParam } from '@/components/performance/performanceFormat';
import { PeriodicChart, WealthChart } from '@/components/performance/PerformanceCharts';
import { CumulativeCard } from '@/components/performance/CumulativeCard';
import {
  BreakdownSection, ComparisonTable, CoverageNotice, ReturnHeatmap, RiskPanel, SummaryCards,
} from '@/components/performance/PerformancePanels';

/**
 * Chave em que a tela guardava a última seleção de benchmarks (D10). Desde 17/10/2026 a tela sempre abre só com o
 * CDI: a seleção vale enquanto está na URL, e a chave antiga é apagada na primeira visita.
 */
const OLD_STORAGE_KEY = 'primevest-rentabilidade-benchmarks';
const DEFAULT_RANGE: PerformanceRange = '1A';
const DEFAULT_BENCH: BenchmarkPick[] = [{ code: 'CDI' }];

/** "CDI,IPCA_MAIS:6" → lista. */
const parseBench = (raw: string | null): BenchmarkPick[] | null => {
  if (!raw) return null;
  const list = raw.split(',').filter(Boolean).map(s => {
    const [code, spread] = s.split(':');
    return spread != null ? { code, spread: Number(spread) } : { code };
  }).filter(b => b.code && (b.spread == null || Number.isFinite(b.spread)));
  return list.length ? list : null;
};

try {
  localStorage.removeItem(OLD_STORAGE_KEY);
} catch { /* navegador sem armazenamento: nada a limpar */ }

/**
 * Rentabilidade comparativa (docs/planos/PLANO_RENTABILIDADE.md, 6.1): a carteira contra os benchmarks, por período,
 * classe e ativo, nominal e real, com risco. Cada bloco responde uma pergunta — quanto rendeu, rendeu mais que o
 * índice, quem puxou o resultado, o poder de compra foi preservado, quanto oscilou. Estado dos filtros na URL; sem
 * eles a tela abre em 12M, bruto e só com o CDI (17/10/2026).
 */
export const PerformancePage = () => {
  const [params, setParams] = useSearchParams();
  const type = params.get('type') ?? '';
  const category = params.get('category') ?? '';
  const position = params.get('position') ?? '';
  const reservation = (params.get('reservation') === 'without' ? 'WITHOUT' : 'WITH') as ReservationFilter;
  // Padrões (17/10/2026): base bruta (`?basis=net` para o líquido) e período de 12 meses (`?range=MAX` para
  // desde o início). Antes eram líquido e desde o início.
  const basis = (params.get('basis') === 'net' ? 'NET' : 'GROSS') as PerformanceBasis;
  const rangeParam = params.get('range') as PerformanceRange | null;
  const range: PerformanceRange = rangeParam && RANGES.includes(rangeParam) ? rangeParam : DEFAULT_RANGE;
  const dateFrom = params.get('from') ?? '';
  const dateTo = params.get('to') ?? '';
  // Sem `benchmarks` na URL, só o CDI (17/10/2026; até então voltava a última seleção guardada no navegador)
  const benchmarks = parseBench(params.get('benchmarks')) ?? DEFAULT_BENCH;
  const [granularity, setGranularity] = useState<Granularity>('MONTH');
  const [level, setLevel] = useState<BreakdownLevel>('TYPE');

  const update = (changes: Record<string, string | null>) => {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)));
    setParams(next, { replace: true });
  };

  const setBenchmarks = (list: BenchmarkPick[]) => {
    update({ benchmarks: benchParam(list) });
  };

  const { data: catalog } = useQuery({ queryKey: ['performance', 'benchmarks'], queryFn: performanceApi.benchmarks });
  const { data: types = [] } = useQuery({ queryKey: ['assets', 'types'], queryFn: assetsApi.getTypes });
  const typeId = types.find(t => t.name === type)?.id;
  const { data: categories = [] } = useQuery({
    queryKey: ['assets', 'categories', typeId],
    queryFn: () => assetsApi.getCategories(typeId),
    enabled: !!typeId,
  });

  const scope: PerformanceQuery = {
    type: type || undefined,
    category: category ? Number(category) : undefined,
    position: position || undefined,
    reservation,
    basis,
  };
  const period = dateFrom || dateTo ? { from: dateFrom || undefined, to: dateTo || undefined } : { range };
  const q: PerformanceQuery = { ...scope, ...period, benchmarks: benchParam(benchmarks) };
  const key = ['performance', JSON.stringify(q)];

  // opções do seletor de ativo: as posições do recorte (sem o próprio filtro de ativo), desde o início
  const { data: positionRows } = useQuery({
    queryKey: ['performance', 'positions', type, category, reservation],
    queryFn: () => performanceApi.breakdown({ type: type || undefined, category: category ? Number(category) : undefined, reservation, range: 'MAX' }, 'POSITION'),
  });
  const summary = useQuery({ queryKey: [...key, 'summary'], queryFn: () => performanceApi.summary(q), placeholderData: p => p });
  const series = useQuery({ queryKey: [...key, 'series'], queryFn: () => performanceApi.series(q), placeholderData: p => p });
  const periodic = useQuery({ queryKey: [...key, 'periodic', granularity], queryFn: () => performanceApi.periodic(q, granularity), placeholderData: p => p });
  const table = useQuery({ queryKey: [...key, 'table'], queryFn: () => performanceApi.table(q), placeholderData: p => p });
  const breakdown = useQuery({ queryKey: [...key, 'breakdown', level], queryFn: () => performanceApi.breakdown(q, level), placeholderData: p => p });
  const heatmap = useQuery({
    queryKey: ['performance', 'heatmap', JSON.stringify(scope)],
    queryFn: () => performanceApi.periodic({ ...scope, range: 'MAX', benchmarks: 'CDI' }, 'MONTH'),
  });
  const wealth = useQuery({ queryKey: [...key, 'wealth'], queryFn: () => performanceApi.wealth(q), placeholderData: p => p });
  const risk = useQuery({ queryKey: [...key, 'risk'], queryFn: () => performanceApi.risk(q), placeholderData: p => p });

  // D4: ao escolher uma classe, os benchmarks dela vão para o começo da lista (o primeiro é o principal). No Tesouro,
  // IPCA e IPCA + a taxa do Tesouro IPCA+ à venda mais curto; um IPCA + x% com outra taxa sai da lista.
  const [lastType, setLastType] = useState(type);
  useEffect(() => {
    if (type === lastType || !catalog) return;
    setLastType(type);
    const defs = parseBench((type && catalog.defaultsByType[type]?.join(',')) || 'CDI') ?? DEFAULT_BENCH;
    const keys = new Set(defs.map(benchKey));
    const replaced = new Set(defs.filter(d => d.spread != null).map(d => d.code));
    const rest = benchmarks.filter(b => !keys.has(benchKey(b)) && !(b.spread != null && replaced.has(b.code)));
    const next = [...defs, ...rest].slice(0, MAX_BENCHMARKS);
    if (benchParam(next) !== benchParam(benchmarks)) setBenchmarks(next);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [type, catalog]);

  const positions: BreakdownRow[] = useMemo(() => {
    const rows = positionRows?.rows ?? [];
    return [...rows].sort((a, b) => a.label.localeCompare(b.label));
  }, [positionRows]);
  const showIssuer = !type || type === 'RENDA_FIXA';
  // a reserva de emergência só existe na renda fixa e no Tesouro (asset_types.fixed_income)
  const reservable = (name: string) => !name || !!types.find(t => t.name === name)?.fixedIncome;
  const showReservation = reservable(type);
  const cov = summary.data?.coverage;

  const drill = (row: BreakdownRow) => {
    if (level === 'TYPE') {
      update({ type: row.typeName, category: null, position: null });
      setLevel('CATEGORY');
    } else if (level === 'CATEGORY' && row.categoryId != null) {
      update({ type: row.typeName, category: String(row.categoryId), position: null });
      setLevel('POSITION');
    } else if (level === 'POSITION' && row.positionId) {
      update({ position: row.positionId });
    }
  };

  const error = summary.error ?? series.error;
  return (
    <div className="space-y-6">
      <PerformanceFilters
        types={types} categories={categories} positions={positions} catalog={catalog}
        type={type} category={category} position={position} reservation={reservation} basis={basis}
        basisApplies={cov?.basisApplies ?? false} showReservation={showReservation} range={range} dateFrom={dateFrom} dateTo={dateTo}
        firstDate={cov?.firstDate} lastDate={cov?.to}
        benchmarks={benchmarks}
        onType={v => update({ type: v || null, category: null, position: null, ...(reservable(v) ? {} : { reservation: null }) })}
        onCategory={v => update({ category: v || null, position: null })}
        onPosition={v => update({ position: v || null })}
        onReservation={v => update({ reservation: v === 'WITHOUT' ? 'without' : null })}
        onBasis={v => update({ basis: v === 'NET' ? 'net' : null })}
        onRange={r => update({ range: r === DEFAULT_RANGE ? null : r, from: null, to: null })}
        onDates={(a, b) => update({ from: a || null, to: b || null })}
        onBenchmarks={setBenchmarks}
      />

      {error ? (
        <div className="rounded-lg border border-danger/40 bg-danger/10 px-4 py-3 text-sm text-white">
          {apiMessage(error) ?? 'Não foi possível calcular a rentabilidade.'}
        </div>
      ) : !summary.data ? (
        <div className="flex h-64 items-center justify-center"><LoadingSpinner /></div>
      ) : (
        <>
          <CoverageNotice coverage={summary.data.coverage} />
          {!summary.data.coverage.empty && (
            <>
              <SummaryCards s={summary.data} />

              <CumulativeCard data={series.data} />

              <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
                <Card title="Rentabilidade por período" titleInfo="Retorno de cada mês, trimestre ou ano dentro do período escolhido, para a carteira e os indicadores.">
                  {periodic.data && <PeriodicChart data={periodic.data} granularity={granularity} onGranularity={setGranularity} />}
                </Card>
                <Card title="Comparativo" titleInfo="Os mesmos números em tabela: mês corrente, ano, últimos 12 meses e o período escolhido.">
                  {table.data && <ComparisonTable t={table.data} />}
                </Card>
              </div>

              <Card title="Quem fez o resultado" titleInfo="Rentabilidade e contribuição de cada classe, categoria, ativo ou emissora. Clique numa linha para detalhar.">
                {breakdown.data && (
                  <BreakdownSection b={breakdown.data} level={level} onLevel={setLevel} showIssuer={showIssuer} onDrill={drill} />
                )}
              </Card>

              <Card title="Mapa de calor mensal" titleInfo="Rentabilidade de cada mês desde o início da carteira (recorte atual).">
                {heatmap.data && <ReturnHeatmap data={heatmap.data} />}
              </Card>

              <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
                <Card title="Patrimônio e poder de compra"
                  titleInfo={`Patrimônio nominal e corrigido pelo IPCA${wealth.data?.realBase ? ` (em reais de ${wealth.data.realBase.slice(5, 7)}/${wealth.data.realBase.slice(0, 4)})` : ''}, contra o que foi investido. A diferença entre patrimônio e investido é o rendimento; os proventos saíram da carteira e estão no balão.`}>
                  {wealth.data && <WealthChart data={wealth.data} />}
                </Card>
                <Card title="Risco" titleInfo="Indicadores calculados com os retornos diários do período. Exigem ao menos 12 meses de histórico sem lacunas.">
                  {risk.data && <RiskPanel r={risk.data} />}
                </Card>
              </div>
            </>
          )}
        </>
      )}
    </div>
  );
};
