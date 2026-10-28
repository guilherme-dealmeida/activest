import { ReactNode, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { issuersApi } from '@/api/issuers';
import { Card } from '@/components/ui/Card';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { Toggle } from '@/components/ui/Toggle';
import { CopyButton } from '@/components/ui/CopyButton';
import { LabelWithInfo } from '@/components/ui/InfoTooltip';
import { CompanyQuarter, CompanyYear, FiiMonth, Issuer } from '@/types';
import { MONTHS, centsDifference, formatCompactCurrency, formatCompactNumber } from '@/utils/indicators';
import { apiMessage } from '@/utils/apiError';

type Fmt = 'money' | 'count' | 'monthPct' | 'brl';

interface Row<T> {
  label: string;
  get: (r: T) => number | null | undefined;
  fmt: Fmt;
  /** Explicação no (i) ao lado do rótulo. */
  info?: string;
}

/** Coluna da tabela: rótulo do cabeçalho e o hover dele. */
interface Column {
  label: string;
  title?: string;
}

const exact = (v: number) => v.toLocaleString('pt-BR', { maximumFractionDigits: 2 });
const fixed = (v: number, d: number) => v.toLocaleString('pt-BR', { minimumFractionDigits: d, maximumFractionDigits: d });

/** Valor simples: abreviado, com o exato no hover (o Valor detalhado saiu em 24/10/2026 — a cópia leva o exato). */
const fmtCell = (v: number | null | undefined, fmt: Fmt): { text: string; title?: string } => {
  if (v == null) return { text: '—' };
  switch (fmt) {
    case 'money': return { text: formatCompactCurrency(v), title: `R$ ${exact(v)}` };
    case 'count': return { text: formatCompactNumber(v), title: exact(v) };
    case 'monthPct': return { text: `${fixed(v * 100, 2)}%` };
    case 'brl': return { text: `R$ ${fixed(v, 2)}` };
  }
};

/**
 * Texto copiado (docs/planos/PLANO_USABILIDADE_PRECO_TETO.md, D2): o valor exato em pt-BR, sem símbolo —
 * "52.445.000.000,00", "-17.040.000.000,00", ações "12.888.732.761", percentual em pontos "0,85". Cola certo nos
 * campos do Activest e no Excel/Sheets em português.
 */
const copyText = (v: number, fmt: Fmt): string => {
  switch (fmt) {
    case 'money': return fixed(v, 2);
    case 'count': return fixed(v, 0);
    case 'monthPct': return fixed(v * 100, 2);
    case 'brl': return fixed(v, 2);
  }
};

/** Diferença em centavos, sem o ruído do ponto flutuante (utils/indicators.ts, testada). */
const minus = centsDifference;

/** Rótulos e (i) do lucro (D8 revisada, 24/10/2026). */
const NET_INCOME_INFO = 'Lucro do grupo inteiro (a companhia e as controladas), incluindo a parte que pertence aos sócios não controladores — os minoritários das controladas.';
const NET_INCOME_PARENT_INFO = 'Parte do lucro consolidado atribuída aos acionistas da companhia. É a base do LPA e do Preço Teto.';
const NET_INCOME_MINORITY_INFO = 'Parte do lucro das controladas que pertence aos sócios minoritários delas; não chega ao acionista da companhia. Calculado: lucro consolidado − lucro da controladora (zero quando a companhia publica só o balanço individual).';

const quarterLabel = (iso: string) => {
  const [y, m] = iso.split('-').map(Number);
  return `${Math.floor((m - 1) / 3) + 1}T${String(y % 100).padStart(2, '0')}`;
};
const monthLabel = (iso: string) => {
  const [y, m] = iso.split('-');
  return `${m}/${y}`;
};
const shortMonth = (iso: string) => MONTHS[Number(iso.slice(5, 7)) - 1].slice(0, 3);

/** "2026 · 6m" no ano em andamento (D8), com "acumulado de jan a jun/2026" no hover. */
const yearColumn = (y: CompanyYear): Column => {
  if (!y.partial) {
    return { label: String(y.fiscalYear), title: `Exercício fechado em ${y.periodEnd.split('-').reverse().join('/')}` };
  }
  const from = y.fiscalYearStart ? shortMonth(y.fiscalYearStart) : '';
  return {
    label: `${y.fiscalYear} · ${y.months}m`,
    title: `Ano em andamento: acumulado de ${from} a ${shortMonth(y.periodEnd)}/${y.periodEnd.slice(0, 4)}; posição do balanço de ${y.periodEnd.split('-').reverse().join('/')}`,
  };
};

const COMPANY_QUARTER_ROWS: Row<CompanyQuarter>[] = [
  { label: 'Receita', get: r => r.revenueQ, fmt: 'money' },
  { label: 'Lucro bruto', get: r => r.grossProfitQ, fmt: 'money' },
  { label: 'EBITDA', get: r => r.ebitdaQ, fmt: 'money' },
  { label: 'EBIT', get: r => r.ebitQ, fmt: 'money' },
  { label: 'Lucro líquido consolidado', get: r => r.netIncomeQ, fmt: 'money', info: NET_INCOME_INFO },
  { label: 'Lucro líquido da controladora', get: r => r.netIncomeParentQ, fmt: 'money', info: NET_INCOME_PARENT_INFO },
  { label: 'Lucro líquido não controladores', get: r => minus(r.netIncomeQ, r.netIncomeParentQ), fmt: 'money', info: NET_INCOME_MINORITY_INFO },
  { label: 'FCO (caixa operacional)', get: r => r.fcoQ, fmt: 'money' },
  { label: 'Dividendos + JCP declarados (DVA)', get: r => r.dividendsDeclaredQ, fmt: 'money' },
  { label: 'Dividendos + JCP pagos (fluxo de caixa)', get: r => r.dividendsPaidQ, fmt: 'money' },
  { label: 'Patrimônio líquido', get: r => r.equity, fmt: 'money' },
  { label: 'PL da controladora', get: r => r.equityParent, fmt: 'money' },
  { label: 'Ativo total', get: r => r.totalAssets, fmt: 'money' },
  { label: 'Ativo circulante', get: r => r.currentAssets, fmt: 'money' },
  { label: 'Passivo circulante', get: r => r.currentLiabilities, fmt: 'money' },
  { label: 'Caixa', get: r => r.cash, fmt: 'money' },
  { label: 'Dívida bruta', get: r => r.grossDebt, fmt: 'money' },
  { label: 'Ações em circulação', get: r => r.sharesOutstanding, fmt: 'count' },
];
const COMPANY_YEAR_ROWS: Row<CompanyYear>[] = [
  { label: 'Receita', get: r => r.revenue, fmt: 'money' },
  { label: 'Lucro bruto', get: r => r.grossProfit, fmt: 'money' },
  { label: 'EBITDA', get: r => r.ebitda, fmt: 'money' },
  { label: 'EBIT', get: r => r.ebit, fmt: 'money' },
  { label: 'Lucro líquido consolidado', get: r => r.netIncome, fmt: 'money', info: NET_INCOME_INFO },
  { label: 'Lucro líquido da controladora', get: r => r.netIncomeParent, fmt: 'money', info: NET_INCOME_PARENT_INFO },
  { label: 'Lucro líquido não controladores', get: r => minus(r.netIncome, r.netIncomeParent), fmt: 'money', info: NET_INCOME_MINORITY_INFO },
  { label: 'FCO (caixa operacional)', get: r => r.fco, fmt: 'money' },
  { label: 'Dividendos + JCP declarados (DVA)', get: r => r.dividendsDeclared, fmt: 'money' },
  { label: 'Dividendos + JCP pagos (fluxo de caixa)', get: r => r.dividendsPaid, fmt: 'money' },
  { label: 'Patrimônio líquido', get: r => r.equity, fmt: 'money' },
  { label: 'PL da controladora', get: r => r.equityParent, fmt: 'money' },
  { label: 'Ativo total', get: r => r.totalAssets, fmt: 'money' },
  { label: 'Ativo circulante', get: r => r.currentAssets, fmt: 'money' },
  { label: 'Passivo circulante', get: r => r.currentLiabilities, fmt: 'money' },
  { label: 'Caixa', get: r => r.cash, fmt: 'money' },
  { label: 'Dívida bruta', get: r => r.grossDebt, fmt: 'money' },
  { label: 'Ações em circulação', get: r => r.sharesOutstanding, fmt: 'count' },
];
const FII_ROWS: Row<FiiMonth>[] = [
  { label: 'Patrimônio líquido', get: r => r.netAssets, fmt: 'money' },
  { label: 'VP por cota', get: r => r.navPerShare, fmt: 'brl' },
  { label: 'Cotas emitidas', get: r => r.sharesIssued, fmt: 'count' },
  { label: 'Cotistas', get: r => r.shareholders, fmt: 'count' },
  { label: 'DY do mês', get: r => r.dividendYieldMonth, fmt: 'monthPct' },
  { label: 'Amortização do mês', get: r => r.amortizationMonth, fmt: 'monthPct' },
  { label: 'Taxa de administração do mês', get: r => r.adminFeeMonth, fmt: 'monthPct' },
  { label: 'Imóveis', get: r => r.realEstateTotal, fmt: 'money' },
  { label: 'Imóveis via SPE', get: r => r.realEstateCompanies, fmt: 'money' },
  { label: 'Recebíveis (CRI, LCI…)', get: r => r.receivablesTotal, fmt: 'money' },
  { label: 'Cotas de FII', get: r => r.fiiSharesTotal, fmt: 'money' },
  { label: 'Liquidez', get: r => r.liquidityTotal, fmt: 'money' },
  { label: 'Passivo', get: r => r.liabilitiesTotal, fmt: 'money' },
  { label: 'Rendimentos a distribuir', get: r => r.incomeToDistribute, fmt: 'money' },
];

function Table<T>({ columns, headers, rows }: { columns: T[]; headers: Column[]; rows: Row<T>[] }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-white/10 text-xs text-gray-400">
            <th className="sticky left-0 z-10 bg-surface px-3 py-2 text-left font-medium">&nbsp;</th>
            {headers.map((h, i) => (
              <th key={i} title={h.title} className={`px-3 py-2 text-right font-medium whitespace-nowrap ${h.title ? 'cursor-help' : ''}`}>
                {h.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map(row => (
            <tr key={row.label} className="border-b border-white/5 last:border-0 hover:bg-white/5">
              <th scope="row" className="sticky left-0 z-10 bg-surface px-3 py-2 text-left font-normal text-gray-300 min-w-[8.5rem] sm:whitespace-nowrap">
                <LabelWithInfo info={row.info}>{row.label}</LabelWithInfo>
              </th>
              {columns.map((c, i) => {
                const v = row.get(c);
                const cell = fmtCell(v, row.fmt);
                return (
                  <td key={i} className={`px-3 py-2 text-right whitespace-nowrap tabular-nums ${v != null && v < 0 ? 'text-danger' : 'text-white'}`}>
                    <span className="inline-flex items-center justify-end gap-1">
                      <span title={cell.title}>{cell.text}</span>
                      {v != null && <CopyButton text={copyText(v, row.fmt)} />}
                    </span>
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

type Basis = 'quarter' | 'year';

/** Colunas por janela (D4, D6 do PLANO_USABILIDADE_PRECO_TETO): companhia e FII. */
const PAGE = 6;

const NavButton = ({ dir, disabled, onClick }: { dir: 'newer' | 'older'; disabled: boolean; onClick: () => void }) => (
  <button type="button" onClick={onClick} disabled={disabled}
          aria-label={dir === 'newer' ? 'Mais recentes' : 'Mais antigos'} title={dir === 'newer' ? 'Mais recentes' : 'Mais antigos'}
          className="rounded-md p-1.5 text-gray-400 hover:bg-white/5 hover:text-white disabled:opacity-30 disabled:hover:bg-transparent disabled:hover:text-gray-400">
    {dir === 'newer' ? <ChevronLeft className="h-5 w-5" /> : <ChevronRight className="h-5 w-5" />}
  </button>
);

/**
 * Demonstrativos (docs/planos/PLANO_TELA_EMISSORES.md, 2.6; docs/planos/PLANO_TELA_ATIVOS.md, T3, T4, D8–D13;
 * docs/planos/PLANO_USABILIDADE_PRECO_TETO.md, S3–S5): série da companhia em Trimestre (trimestre isolado) | Anual
 * (exercício fechado e o ano em andamento acumulado) ou informe mensal do FII. Valor simples com o exato no hover e
 * ícone de copiar o exato. Mais recente à esquerda, janela de 6 colunas: › anda 6 para os mais antigos, ‹ volta para
 * os mais recentes (D5), sem passar das pontas.
 */
export const FinancialStatements = ({ issuer }: { issuer: Issuer }) => {
  const company = issuer.kind === 'COMPANY';
  const [basis, setBasis] = useState<Basis>('quarter');
  const [offset, setOffset] = useState(0);
  const annual = company && basis === 'year';

  const quarters = useQuery({
    queryKey: ['issuer-quarters', issuer.id],
    queryFn: () => issuersApi.quarters(issuer.id),
    enabled: company && !annual,
    staleTime: 10 * 60_000,
  });
  const years = useQuery({
    queryKey: ['issuer-years', issuer.id],
    queryFn: () => issuersApi.years(issuer.id),
    enabled: annual,
    staleTime: 10 * 60_000,
  });
  const months = useQuery({
    queryKey: ['issuer-months', issuer.id],
    queryFn: () => issuersApi.months(issuer.id),
    enabled: !company,
    staleTime: 10 * 60_000,
  });
  const q = !company ? months : annual ? years : quarters;
  const total = (!company ? months.data?.length : annual ? years.data?.length : quarters.data?.length) ?? 0;
  // a janela nunca passa do fim: a última mostra as 6 mais antigas
  const start = Math.max(0, Math.min(offset, total - PAGE));
  const end = Math.min(total, start + PAGE);
  const visible = <T,>(list: T[] | undefined) => [...(list ?? [])].reverse().slice(start, end);

  let body: ReactNode;
  let headers: Column[] = [];
  if (q.isLoading) {
    body = <div className="flex h-40 items-center justify-center"><LoadingSpinner className="h-6 w-6 text-accent" /></div>;
  } else if (q.isError) {
    body = <p className="text-sm text-gray-400">{apiMessage(q.error) ?? 'Não foi possível carregar os demonstrativos.'}</p>;
  } else if (total === 0) {
    body = <p className="text-sm text-gray-400">{company ? 'Nenhum balanço importado para esta companhia.' : 'Nenhum informe mensal da CVM para este fundo.'}</p>;
  } else if (annual) {
    const cols = visible(years.data);
    headers = cols.map(yearColumn);
    body = <Table columns={cols} headers={headers} rows={COMPANY_YEAR_ROWS} />;
  } else if (company) {
    const cols = visible(quarters.data);
    headers = cols.map(c => ({ label: quarterLabel(c.periodEnd) }));
    body = <Table columns={cols} headers={headers} rows={COMPANY_QUARTER_ROWS} />;
  } else {
    const cols = visible(months.data);
    headers = cols.map(c => ({ label: monthLabel(c.referenceMonth) }));
    body = <Table columns={cols} headers={headers} rows={FII_ROWS} />;
  }

  return (
    <Card>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h3 className="text-sm font-medium text-gray-400">
          {company ? 'Demonstrativos (CVM — DFP/ITR)' : 'Informe mensal (CVM)'}
        </h3>
        <div className="flex flex-wrap items-center gap-2">
          {company && (
            <Toggle label="Base" value={basis} onChange={v => { setBasis(v); setOffset(0); }}
                    options={[{ value: 'quarter', label: 'Trimestre' }, { value: 'year', label: 'Anual' }]} />
          )}
          {total > PAGE && headers.length > 0 && (
            <div className="flex items-center gap-1">
              {/* D7: posição da janela */}
              <span className="mr-1 text-xs text-gray-500 tabular-nums whitespace-nowrap">
                {headers[0].label} – {headers[headers.length - 1].label} · {end - start} de {total}
              </span>
              <NavButton dir="newer" disabled={start === 0} onClick={() => setOffset(Math.max(0, start - PAGE))} />
              <NavButton dir="older" disabled={end >= total} onClick={() => setOffset(start + PAGE)} />
            </div>
          )}
        </div>
      </div>
      {body}
      {total > 0 && (
        <p className="mt-3 text-xs text-gray-500">
          {company && (annual
            ? 'Anual: exercício fechado (DFP); o ano em andamento soma os trimestres desde o início do exercício. '
            : 'Receita a dividendos: trimestre isolado. ')}
          {company && 'PL, ativo, circulantes, caixa, dívida e ações: posição na data do balanço. EBITDA = EBIT + depreciação e amortização, sem a exaustão dos ativos biológicos. '}
          Passe o mouse para ver o valor exato; o ícone ao lado copia o valor exato
          {company && ' (em reais; a CVM publica quase todos os balanços em R$ mil, por isso os três últimos dígitos costumam ser zeros)'}.
        </p>
      )}
    </Card>
  );
};
