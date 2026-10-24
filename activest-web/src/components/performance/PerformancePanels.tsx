import { Link } from 'react-router-dom';
import { AlertTriangle, Info } from 'lucide-react';
import { AssetLogo } from '@/components/ui/AssetLogo';
import { InfoTooltip } from '@/components/ui/InfoTooltip';
import { useTheme } from '@/context/ThemeContext';
import { formatCurrency, getReturnColor } from '@/utils/format';
import { typeLogo } from '@/utils/assetTypeRules';
import {
  BreakdownLevel, Coverage, PerformanceBreakdown, PerformancePeriodic, PerformanceRisk, PerformanceSummary,
  PerformanceTable,
} from '@/types/performance';
import { MONTHS, date, monthYear, pct, pp } from './performanceFormat';

// ------------------------------------------------------------------ cobertura

/** O que o resultado cobre (R10, critério 10): lacunas, preço de último negócio, estimativa, real até quando. */
export const CoverageNotice = ({ coverage }: { coverage: Coverage }) => {
  if (coverage.empty) {
    return (
      <div className="rounded-lg border border-white/10 bg-white/5 px-4 py-3 text-sm text-gray-300">
        Sem movimentação no recorte e período escolhidos.
      </div>
    );
  }
  const missing = coverage.gaps.filter(g => g.status === 'SEM_PRECO');
  const stale = coverage.gaps.filter(g => g.status === 'ULTIMO_NEGOCIO');
  const estimated = coverage.gaps.filter(g => g.status === 'ESTIMADO');
  return (
    <div className="space-y-2">
      {missing.length > 0 && (
        <div className="flex gap-2 rounded-lg border border-danger/40 bg-danger/10 px-4 py-3 text-sm text-white" role="alert">
          <AlertTriangle className="h-4 w-4 shrink-0 text-danger mt-0.5" aria-hidden="true" />
          <div>
            <strong>Resultado indisponível no período:</strong> sem preço de{' '}
            {missing.map((g, i) => (
              <span key={`${g.ticker}${g.from}`}>{i > 0 ? '; ' : ''}{g.ticker} ({date(g.from)} a {date(g.to)})</span>
            ))}.
            {' '}O Activest não calcula a rentabilidade sem esses ativos para não mostrar um número distorcido —
            filtre o recorte sem eles para ver o restante.
          </div>
        </div>
      )}
      <div className="flex gap-2 rounded-lg border border-white/10 bg-white/5 px-4 py-2.5 text-xs text-gray-300">
        <Info className="h-4 w-4 shrink-0 text-gray-400" aria-hidden="true" />
        <div className="space-y-0.5">
          {stale.length > 0 && (
            <div>Sem negócio em algum dia: vale o último fechamento (até 5 pregões) —{' '}
              {[...new Set(stale.map(g => g.ticker))].slice(0, 6).join(', ')}{stale.length > 6 ? '…' : ''}.</div>
          )}
          {estimated.length > 0 && (
            <div>Renda fixa indexada à inflação com o mês corrente estimado pelo último índice publicado.</div>
          )}
          {coverage.realUntil && <div>Rentabilidade real e IPCA até {monthYear(coverage.realUntil)} (último IPCA publicado).</div>}
          <div>Os índices são medidos de fechamento a fechamento; a carteira conta cada compra a partir do preço pago.
            Por isso um ativo pode render diferente do índice que replica: a diferença vem do preço de compra em relação
            ao fechamento do dia.</div>
        </div>
      </div>
    </div>
  );
};

// ------------------------------------------------------------------ cards

const Stat = ({ title, info, value, sub, tone }: {
  title: string; info: string; value: string; sub?: React.ReactNode; tone?: number | null;
}) => (
  <div className="bg-surface rounded-xl border border-surface-light p-4">
    <div className="text-xs font-medium text-gray-400 flex items-center gap-1">{title}<InfoTooltip content={info} /></div>
    <div className={`mt-1 text-2xl font-semibold ${tone != null ? getReturnColor(tone) : 'text-white'}`}>{value}</div>
    {sub && <div className="mt-1 text-xs text-gray-400">{sub}</div>}
  </div>
);

/** Cards de desempenho (E04 item 1). */
export const SummaryCards = ({ s }: { s: PerformanceSummary }) => {
  const basis = s.coverage.basis === 'GROSS' ? 'bruta' : 'líquida';
  const primary = s.primary;
  return (
    <div className="grid grid-cols-2 lg:grid-cols-3 xl:grid-cols-6 gap-4">
      <Stat title="Rentabilidade" tone={s.twr} value={pct(s.twr)}
        info={`Retorno ponderado pelo tempo (TWR) da carteira ${basis} no período: aportes e retiradas não contam como rendimento; os proventos entram no pregão ex. É a medida comparável com os índices.`}
        sub={s.result != null ? <span className={getReturnColor(s.result)}>{formatCurrency(s.result)}</span> : undefined} />
      <Stat title={primary ? primary.name : 'Benchmark'} value={pct(primary?.value)}
        info="O benchmark principal (o primeiro da lista) no mesmo período efetivo da carteira."
        sub={primary?.partial ? `até ${date(primary.until)}` : undefined} />
      <Stat title="Diferença" tone={s.diff} value={pp(s.diff)}
        info={`Rentabilidade da carteira menos a do benchmark principal, em pontos percentuais.${s.coverage.basisApplies && s.coverage.basis === 'NET' ? ' Atenção: carteira líquida contra índice bruto — para uma comparação equivalente, use a base bruta.' : ''}`}
        sub={s.coverage.basisApplies ? `carteira ${basis} × índice bruto` : undefined} />
      <Stat title="Rentabilidade real" tone={s.real} value={pct(s.real)}
        info="(1 + rentabilidade) ÷ (1 + IPCA) − 1, com o IPCA do mesmo intervalo. Só até o último IPCA publicado."
        sub={s.realUntil ? `IPCA ${pct(s.inflation)} até ${monthYear(s.realUntil)}` : undefined} />
      <Stat title="% do CDI" value={s.percentOfCdi != null ? `${s.percentOfCdi.toLocaleString('pt-BR', { maximumFractionDigits: 2 })}%` : '—'}
        info="Rentabilidade da carteira ÷ CDI acumulado no mesmo período × 100. Só com CDI acumulado positivo; carteira negativa dá percentual negativo."
        sub={`CDI ${pct(s.cdi)}`} />
      <Stat title="Retorno pessoal (a.a.)" tone={s.xirr} value={pct(s.xirr)}
        info="Taxa interna de retorno dos seus fluxos (XIRR): aportes, retiradas, proventos na data de pagamento e o saldo final. Mede o resultado do seu dinheiro, com o momento de cada aporte — por isso difere do TWR. Exige ao menos 12 meses."
        sub={s.xirrNote ?? undefined} />
    </div>
  );
};

// ------------------------------------------------------------------ tabela comparativa

/** Tabela comparativa (E04 item 4): mês, ano, 12 meses e período, com a diferença para a carteira. */
export const ComparisonTable = ({ t }: { t: PerformanceTable }) => (
  <div className="overflow-x-auto">
    <table className="w-full text-sm">
      <thead>
        <tr className="border-b border-white/10 text-gray-400">
          <th className="text-left py-2 px-3 font-medium">Indicador</th>
          <th className="text-right py-2 px-3 font-medium">No mês</th>
          <th className="text-right py-2 px-3 font-medium">No ano</th>
          <th className="text-right py-2 px-3 font-medium">12 meses</th>
          <th className="text-right py-2 px-3 font-medium">No período</th>
          <th className="text-right py-2 px-3 font-medium">
            <span className="inline-flex items-center gap-1">Carteira − indicador
              <InfoTooltip content="Diferença no período, em pontos percentuais. Positiva: a carteira rendeu mais." /></span>
          </th>
        </tr>
      </thead>
      <tbody>
        {t.rows.map(r => (
          <tr key={r.code} className={`border-b border-white/5 ${r.portfolio ? 'font-semibold' : ''}`}>
            <td className="py-2 px-3 text-white">{r.name}{r.until && <span className="text-gray-500 font-normal"> *</span>}</td>
            {[r.month, r.year, r.months12, r.period].map((v, i) => (
              <td key={i} className={`py-2 px-3 text-right ${v != null ? getReturnColor(v) : 'text-gray-500'}`}>{pct(v)}</td>
            ))}
            <td className={`py-2 px-3 text-right ${r.diffPeriod != null ? getReturnColor(r.diffPeriod) : 'text-gray-500'}`}>
              {r.portfolio ? '' : pp(r.diffPeriod)}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
    {t.rows.filter(r => r.until).map(r => (
      <p key={r.code} className="mt-2 text-xs text-gray-500">
        * {r.name}: até {date(r.until)} (último dado publicado); a diferença compara a carteira no mesmo intervalo.
      </p>
    ))}
    <p className="mt-2 text-xs text-gray-500">
      No mês desde {date(t.monthFrom)} · no ano desde {date(t.yearFrom)} · 12 meses desde {date(t.months12From)}
      {' '}(ou desde o início da carteira, se mais recente).
    </p>
  </div>
);

// ------------------------------------------------------------------ classe e ativo

const LEVELS: { key: BreakdownLevel; label: string }[] = [
  { key: 'TYPE', label: 'Por classe' },
  { key: 'CATEGORY', label: 'Por categoria' },
  { key: 'POSITION', label: 'Por ativo' },
  { key: 'ISSUER', label: 'Por emissora (RF)' },
];

const HEADER: Record<BreakdownLevel, string> = { TYPE: 'Classe', CATEGORY: 'Categoria', POSITION: 'Ativo', ISSUER: 'Emissora' };

/**
 * Análise por classe, categoria, ativo e emissora (E05): rentabilidade individual e contribuição lado a lado, nunca
 * na mesma coluna; clique numa linha para descer (classe → categoria → ativo).
 */
export const BreakdownSection = ({ b, level, onLevel, showIssuer, onDrill }: {
  b: PerformanceBreakdown; level: BreakdownLevel; onLevel: (l: BreakdownLevel) => void; showIssuer: boolean;
  onDrill: (row: PerformanceBreakdown['rows'][number]) => void;
}) => {
  const maxAbs = Math.max(0.0001, ...b.rows.map(r => Math.abs(r.contribution ?? 0)));
  return (
    <div>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <div className="flex gap-1" role="tablist" aria-label="Agrupamento da análise">
          {LEVELS.filter(l => l.key !== 'ISSUER' || showIssuer).map(l => (
            <button key={l.key} type="button" role="tab" aria-selected={level === l.key} onClick={() => onLevel(l.key)}
              className={`rounded-md px-2.5 py-1 text-xs font-medium ${level === l.key ? 'bg-accent text-black' : 'bg-white/5 text-gray-300 hover:bg-white/10'}`}>
              {l.label}
            </button>
          ))}
        </div>
        <span className="text-xs text-gray-400">
          Carteira {pct(b.total)} · resultado {b.totalGain != null ? formatCurrency(b.totalGain) : '—'}
        </span>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-white/10 text-gray-400">
              <th className="text-left py-2 px-3 font-medium">{HEADER[level]}</th>
              <th className="text-right py-2 px-3 font-medium"><span className="inline-flex items-center gap-1">Rentabilidade
                <InfoTooltip content="Rentabilidade do próprio grupo (TWR) nos dias em que ele esteve na carteira." /></span></th>
              <th className="text-right py-2 px-3 font-medium"><span className="inline-flex items-center gap-1">Peso médio
                <InfoTooltip content="Participação média no capital da carteira durante o período." /></span></th>
              <th className="text-right py-2 px-3 font-medium">Resultado</th>
              <th className="py-2 px-3 font-medium min-w-[180px]"><span className="inline-flex items-center gap-1">Contribuição
                <InfoTooltip content="Quanto o grupo somou à rentabilidade da carteira, em pontos percentuais (a soma das linhas dá a rentabilidade total). Um ativo pode render muito e contribuir pouco, se pesou pouco." /></span></th>
              <th className="text-right py-2 px-3 font-medium">Valor atual</th>
            </tr>
          </thead>
          <tbody>
            {b.rows.map(r => {
              const logo = typeLogo(r.typeName);
              const c = r.contribution ?? 0;
              const width = `${Math.abs(c) / maxAbs * 50}%`;
              return (
                <tr key={r.key} className="border-b border-white/5 hover:bg-white/5 cursor-pointer" onClick={() => onDrill(r)}>
                  <td className="py-2 px-3">
                    <div className="flex items-center gap-2">
                      {level === 'POSITION' && r.ticker && (
                        <AssetLogo ticker={r.ticker} hasLogo={!!r.hasLogo} src={logo?.src} label={logo?.label} />
                      )}
                      <div>
                        <div className="text-white font-medium">
                          {level === 'POSITION' && r.ticker
                            ? <Link to={`/transactions?position=${r.positionId}`} onClick={e => e.stopPropagation()} className="hover:underline">{r.label}</Link>
                            : r.label}
                          {r.closed && <span className="ml-2 rounded bg-white/10 px-1.5 py-0.5 text-[10px] text-gray-300">encerrada</span>}
                        </div>
                        {r.detail && <div className="text-xs text-gray-500">{r.detail}</div>}
                      </div>
                    </div>
                  </td>
                  <td className={`py-2 px-3 text-right ${r.individual != null ? getReturnColor(r.individual) : 'text-gray-500'}`}>{pct(r.individual)}</td>
                  <td className="py-2 px-3 text-right text-gray-300">{r.weight != null ? pct(r.weight, 1, false) : '—'}</td>
                  <td className={`py-2 px-3 text-right ${r.gain != null ? getReturnColor(r.gain) : 'text-gray-500'}`}>{r.gain != null ? formatCurrency(r.gain) : '—'}</td>
                  <td className="py-2 px-3">
                    <div className="flex items-center gap-2">
                      <div className="relative h-2.5 flex-1 rounded bg-white/5" aria-hidden="true">
                        <div className="absolute inset-y-0 left-1/2 w-px bg-white/20" />
                        <div className={`absolute inset-y-0 rounded ${c >= 0 ? 'left-1/2 bg-success' : 'right-1/2 bg-danger'}`} style={{ width }} />
                      </div>
                      <span className={`w-20 text-right text-xs ${r.contribution != null ? getReturnColor(c) : 'text-gray-500'}`}>{pp(r.contribution)}</span>
                    </div>
                  </td>
                  <td className="py-2 px-3 text-right text-white">{formatCurrency(r.valueEnd)}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
};

// ------------------------------------------------------------------ mapa de calor

/** Mapa de calor mês × ano da carteira (L15). Azul = positivo, vermelho = negativo, cinza = zero. */
export const ReturnHeatmap = ({ data }: { data: PerformancePeriodic }) => {
  const { isDark } = useTheme();
  const byYear = new Map<number, Map<number, { v: number | null; partial: boolean }>>();
  data.rows.forEach(r => {
    const [y, m] = r.key.split('-').map(Number);
    if (!byYear.has(y)) byYear.set(y, new Map());
    byYear.get(y)!.set(m, { v: r.portfolio, partial: r.partial });
  });
  const years = [...byYear.keys()].sort((a, b) => b - a);
  const cap = 4; // % que satura a cor
  const mid = isDark ? [56, 56, 53] : [240, 239, 236];
  const pos = isDark ? [57, 135, 229] : [42, 120, 214];
  const neg = isDark ? [230, 103, 103] : [227, 73, 72];
  const bg = (v: number | null) => {
    if (v == null) return 'transparent';
    const k = Math.min(1, Math.abs(v) / cap);
    const pole = v >= 0 ? pos : neg;
    const c = mid.map((m, i) => Math.round(m + (pole[i] - m) * k));
    return `rgb(${c.join(',')})`;
  };
  const yearReturn = (m: Map<number, { v: number | null }>) => {
    let f = 1;
    for (const { v } of m.values()) {
      if (v == null) return null;
      f *= 1 + v / 100;
    }
    return (f - 1) * 100;
  };
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-xs border-separate" style={{ borderSpacing: 2 }}>
        <thead>
          <tr className="text-gray-400">
            <th className="text-left font-medium px-1">Ano</th>
            {MONTHS.map(m => <th key={m} className="font-medium px-1">{m}</th>)}
            <th className="font-medium px-1">Ano</th>
          </tr>
        </thead>
        <tbody>
          {years.map(y => {
            const months = byYear.get(y)!;
            const yr = yearReturn(months);
            return (
              <tr key={y}>
                <td className="px-1 text-gray-300 font-medium">{y}</td>
                {MONTHS.map((_, i) => {
                  const cell = months.get(i + 1);
                  const strong = cell?.v != null && Math.abs(cell.v) / cap > 0.55;
                  return (
                    <td key={i} className={`rounded px-1 py-1.5 text-center ${strong ? 'text-white' : 'text-gray-200'}`}
                      style={{ background: bg(cell?.v ?? null) }}
                      title={cell ? `${MONTHS[i]}/${y}: ${pct(cell.v)}${cell.partial ? ' (mês incompleto)' : ''}` : undefined}>
                      {cell ? `${cell.v != null ? cell.v.toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 }) : '—'}${cell.partial ? '*' : ''}` : ''}
                    </td>
                  );
                })}
                <td className={`px-1 text-center font-semibold ${yr != null ? getReturnColor(yr) : 'text-gray-500'}`}>
                  {yr != null ? pct(yr, 1) : '—'}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      <p className="mt-2 text-xs text-gray-500">Em %. * mês incompleto (início da carteira ou mês corrente). A coluna "Ano" compõe os meses mostrados.</p>
    </div>
  );
};

// ------------------------------------------------------------------ risco

/** Indicadores de risco (E07, L14). */
export const RiskPanel = ({ r }: { r: PerformanceRisk }) => {
  if (r.volatility == null) {
    return <p className="text-sm text-gray-400">{r.note ?? 'Sem dados suficientes.'}</p>;
  }
  const item = (title: string, value: string, info: string, sub?: string) => (
    <div className="rounded-lg bg-white/5 p-3">
      <div className="text-xs text-gray-400 flex items-center gap-1">{title}<InfoTooltip content={info} /></div>
      <div className="mt-1 text-lg font-semibold text-white">{value}</div>
      {sub && <div className="text-xs text-gray-500">{sub}</div>}
    </div>
  );
  const num = (v: number | null) => (v == null ? '—' : v.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }));
  return (
    <div>
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        {item('Volatilidade anualizada', pct(r.volatility, 2, false), 'Desvio-padrão dos retornos diários × √252: o quanto a carteira oscila.')}
        {item('Máximo drawdown', pct(r.maxDrawdown), 'Maior queda do acumulado desde um pico até o vale seguinte.', r.peak && r.trough ? `${date(r.peak)} → ${date(r.trough)}` : undefined)}
        {item('Índice de Sharpe', num(r.sharpe), 'Retorno anualizado acima do CDI dividido pela volatilidade: quanto de retorno extra por unidade de risco.', `retorno ${pct(r.annualReturn)} a.a. · CDI ${pct(r.annualCdi)} a.a.`)}
        {item('Índice de Sortino', num(r.sortino), 'Como o Sharpe, mas só conta as oscilações abaixo do CDI (risco de queda).')}
      </div>
      <p className="mt-2 text-xs text-gray-500">
        {r.days} pregões de {date(r.from)} a {date(r.to)}. {r.note}
      </p>
    </div>
  );
};
