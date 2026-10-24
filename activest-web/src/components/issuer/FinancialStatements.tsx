import { ReactNode, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { issuersApi } from '@/api/issuers';
import { Card } from '@/components/ui/Card';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { Toggle } from '@/components/ui/Toggle';
import { CompanyQuarter, CompanyYear, FiiMonth, Issuer } from '@/types';
import { MONTHS, formatCompactCurrency, formatCompactNumber, formatFull } from '@/utils/indicators';
import { apiMessage } from '@/utils/apiError';

type Fmt = 'money' | 'count' | 'monthPct' | 'brl';

interface Row<T> {
  label: string;
  get: (r: T) => number | null | undefined;
  fmt: Fmt;
}

/** Coluna da tabela: rótulo do cabeçalho e o hover dele. */
interface Column {
  label: string;
  title?: string;
}

const exact = (v: number) => v.toLocaleString('pt-BR', { maximumFractionDigits: 2 });

/**
 * Valor simples: abreviado, com o exato no hover. Valor detalhado (D12): o número inteiro, com todas as casas, só no
 * que hoje é abreviado (dinheiro e quantidade); percentual e R$ por cota já são completos.
 */
const fmtCell = (v: number | null | undefined, fmt: Fmt, detailed: boolean): { text: string; title?: string } => {
  if (v == null) return { text: '—' };
  switch (fmt) {
    case 'money': return detailed ? { text: formatFull(v, 'money') } : { text: formatCompactCurrency(v), title: `R$ ${exact(v)}` };
    case 'count': return detailed ? { text: formatFull(v, 'count') } : { text: formatCompactNumber(v), title: exact(v) };
    case 'monthPct': return { text: `${(v * 100).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%` };
    case 'brl': return { text: `R$ ${v.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` };
  }
};

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
  { label: 'Lucro líquido', get: r => r.netIncomeQ, fmt: 'money' },
  { label: 'Lucro da controladora', get: r => r.netIncomeParentQ, fmt: 'money' },
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
  { label: 'Lucro líquido', get: r => r.netIncome, fmt: 'money' },
  { label: 'Lucro da controladora', get: r => r.netIncomeParent, fmt: 'money' },
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

function Table<T>({ columns, headers, rows, detailed }: {
  columns: T[]; headers: Column[]; rows: Row<T>[]; detailed: boolean;
}) {
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
                {row.label}
              </th>
              {columns.map((c, i) => {
                const v = row.get(c);
                const cell = fmtCell(v, row.fmt, detailed);
                // detalhado: fonte menor nos valores (D11, 23/10/2026) — o número inteiro e longo
                const small = detailed && (row.fmt === 'money' || row.fmt === 'count');
                return (
                  <td key={i} title={cell.title}
                      className={`px-3 py-2 text-right whitespace-nowrap tabular-nums ${small ? 'text-[11px]' : ''} ${v != null && v < 0 ? 'text-danger' : 'text-white'}`}>
                    {cell.text}
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

/**
 * Demonstrativos (docs/planos/PLANO_TELA_EMISSORES.md, 2.6; docs/planos/PLANO_TELA_ATIVOS.md, T3, T4, D8–D13): série
 * da companhia em Trimestre (trimestre isolado) | Anual (exercício fechado e o ano em andamento acumulado) ou informe
 * mensal do FII. Valor simples (padrão) | detalhado, sem lembrar a escolha. Mais recente à esquerda; padrão 8
 * colunas (12 no FII), com "Todos".
 */
export const FinancialStatements = ({ issuer }: { issuer: Issuer }) => {
  const company = issuer.kind === 'COMPANY';
  const [basis, setBasis] = useState<Basis>('quarter');
  const [all, setAll] = useState(false);
  const [detailed, setDetailed] = useState(false);
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
  const limit = company ? 8 : 12;
  const total = (!company ? months.data?.length : annual ? years.data?.length : quarters.data?.length) ?? 0;

  let body: ReactNode;
  if (q.isLoading) {
    body = <div className="flex h-40 items-center justify-center"><LoadingSpinner className="h-6 w-6 text-accent" /></div>;
  } else if (q.isError) {
    body = <p className="text-sm text-gray-400">{apiMessage(q.error) ?? 'Não foi possível carregar os demonstrativos.'}</p>;
  } else if (total === 0) {
    body = <p className="text-sm text-gray-400">{company ? 'Nenhum balanço importado para esta companhia.' : 'Nenhum informe mensal da CVM para este fundo.'}</p>;
  } else if (annual) {
    const cols = [...years.data!].reverse().slice(0, all ? undefined : limit);
    body = <Table columns={cols} headers={cols.map(yearColumn)} rows={COMPANY_YEAR_ROWS} detailed={detailed} />;
  } else if (company) {
    const cols = [...quarters.data!].reverse().slice(0, all ? undefined : limit);
    body = <Table columns={cols} headers={cols.map(c => ({ label: quarterLabel(c.periodEnd) }))}
                  rows={COMPANY_QUARTER_ROWS} detailed={detailed} />;
  } else {
    const cols = [...months.data!].reverse().slice(0, all ? undefined : limit);
    body = <Table columns={cols} headers={cols.map(c => ({ label: monthLabel(c.referenceMonth) }))} rows={FII_ROWS}
                  detailed={detailed} />;
  }

  return (
    <Card>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h3 className="text-sm font-medium text-gray-400">
          {company ? 'Demonstrativos (CVM — DFP/ITR)' : 'Informe mensal (CVM)'}
        </h3>
        <div className="flex flex-wrap items-center gap-2">
          <Toggle label="Formato dos valores" value={detailed ? 'detailed' : 'simple'} onChange={v => setDetailed(v === 'detailed')}
                  options={[{ value: 'simple', label: 'Valor simples' }, { value: 'detailed', label: 'Valor detalhado' }]} />
          {company && (
            <Toggle label="Base" value={basis} onChange={v => { setBasis(v); setAll(false); }}
                    options={[{ value: 'quarter', label: 'Trimestre' }, { value: 'year', label: 'Anual' }]} />
          )}
          {total > limit && (
            <Toggle label="Quantidade" value={all ? 'all' : 'last'} onChange={v => setAll(v === 'all')}
                    options={[{ value: 'last', label: `Últimos ${limit}` }, { value: 'all', label: `Todos (${total})` }]} />
          )}
        </div>
      </div>
      {body}
      {company && total > 0 && (
        <p className="mt-3 text-xs text-gray-500">
          {annual
            ? 'Anual: exercício fechado (DFP); o ano em andamento soma os trimestres desde o início do exercício. '
            : 'Receita a dividendos: trimestre isolado. '}
          PL, ativo, circulantes, caixa, dívida e ações: posição na data do balanço. EBITDA = EBIT + depreciação e
          amortização, sem a exaustão dos ativos biológicos.{!detailed && ' Passe o mouse para ver o valor exato.'}
          {/* D23 (24/10/2026): o detalhado não tem mais precisão que a fonte */}
          {detailed && ' Valor detalhado: a CVM publica quase todos os balanços em R$ mil, por isso os três últimos dígitos costumam ser zeros.'}
        </p>
      )}
    </Card>
  );
};
