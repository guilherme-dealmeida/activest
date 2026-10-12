import { ReactNode, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { issuersApi } from '@/api/issuers';
import { Card } from '@/components/ui/Card';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { CompanyQuarter, FiiMonth, Issuer } from '@/types';
import { formatCompactCurrency, formatCompactNumber } from '@/utils/indicators';
import { apiMessage } from '@/utils/apiError';

type Fmt = 'money' | 'count' | 'monthPct' | 'brl';

interface Row<T> {
  label: string;
  get: (r: T) => number | null;
  fmt: Fmt;
}

const exact = (v: number) => v.toLocaleString('pt-BR', { maximumFractionDigits: 2 });

const fmtCell = (v: number | null, fmt: Fmt): { text: string; title?: string } => {
  if (v == null || v === undefined) return { text: '—' };
  switch (fmt) {
    case 'money': return { text: formatCompactCurrency(v), title: `R$ ${exact(v)}` };
    case 'count': return { text: formatCompactNumber(v), title: exact(v) };
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

const COMPANY_FLOW = (ttm: boolean): Row<CompanyQuarter>[] => [
  { label: 'Receita', get: r => (ttm ? r.revenueTtm : r.revenueQ), fmt: 'money' },
  { label: 'EBIT', get: r => (ttm ? r.ebitTtm : r.ebitQ), fmt: 'money' },
  { label: 'Lucro líquido', get: r => (ttm ? r.netIncomeTtm : r.netIncomeQ), fmt: 'money' },
  { label: 'Lucro da controladora', get: r => (ttm ? r.netIncomeParentTtm : r.netIncomeParentQ), fmt: 'money' },
  { label: 'FCO (caixa operacional)', get: r => (ttm ? r.fcoTtm : r.fcoQ), fmt: 'money' },
  { label: 'Dividendos + JCP declarados (DVA)', get: r => (ttm ? r.dividendsDeclaredTtm : r.dividendsDeclaredQ), fmt: 'money' },
  { label: 'Dividendos + JCP pagos (fluxo de caixa)', get: r => (ttm ? r.dividendsPaidTtm : r.dividendsPaidQ), fmt: 'money' },
];
const COMPANY_POSITION: Row<CompanyQuarter>[] = [
  { label: 'Patrimônio líquido', get: r => r.equity, fmt: 'money' },
  { label: 'PL da controladora', get: r => r.equityParent, fmt: 'money' },
  { label: 'Ativo total', get: r => r.totalAssets, fmt: 'money' },
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

function Table<T>({ columns, labels, rows }: { columns: T[]; labels: string[]; rows: Row<T>[] }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-white/10 text-xs text-gray-400">
            <th className="sticky left-0 z-10 bg-surface px-3 py-2 text-left font-medium">&nbsp;</th>
            {labels.map((l, i) => <th key={i} className="px-3 py-2 text-right font-medium whitespace-nowrap">{l}</th>)}
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
                const cell = fmtCell(v, row.fmt);
                return (
                  <td key={i} title={cell.title}
                      className={`px-3 py-2 text-right whitespace-nowrap tabular-nums ${v != null && v < 0 ? 'text-danger' : 'text-white'}`}>
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

const Toggle = ({ options, value, onChange, label }: {
  options: { value: string; label: string }[]; value: string; onChange: (v: string) => void; label: string;
}) => (
  <div className="flex rounded-md bg-white/5 p-0.5" role="group" aria-label={label}>
    {options.map(o => (
      <button key={o.value} type="button" onClick={() => onChange(o.value)} aria-pressed={value === o.value}
              className={`rounded px-3 py-1 text-xs font-medium transition-colors ${
                value === o.value ? 'bg-accent text-black' : 'text-gray-300 hover:text-white'
              }`}>
        {o.label}
      </button>
    ))}
  </div>
);

/**
 * Demonstrativos (docs/planos/PLANO_TELA_EMISSORES.md, 2.6, D4): série trimestral da
 * companhia (trimestre | 12 meses) ou informe mensal do FII. Mais recente à
 * esquerda; padrão 8 trimestres / 12 meses, com "ver todos".
 */
export const FinancialStatements = ({ issuer }: { issuer: Issuer }) => {
  const company = issuer.kind === 'COMPANY';
  const [ttm, setTtm] = useState(false);
  const [all, setAll] = useState(false);

  const quarters = useQuery({
    queryKey: ['issuer-quarters', issuer.id],
    queryFn: () => issuersApi.quarters(issuer.id),
    enabled: company,
    staleTime: 10 * 60_000,
  });
  const months = useQuery({
    queryKey: ['issuer-months', issuer.id],
    queryFn: () => issuersApi.months(issuer.id),
    enabled: !company,
    staleTime: 10 * 60_000,
  });
  const q = company ? quarters : months;
  const limit = company ? 8 : 12;
  const total = (company ? quarters.data?.length : months.data?.length) ?? 0;

  let body: ReactNode;
  if (q.isLoading) {
    body = <div className="flex h-40 items-center justify-center"><LoadingSpinner className="h-6 w-6 text-accent" /></div>;
  } else if (q.isError) {
    body = <p className="text-sm text-gray-400">{apiMessage(q.error) ?? 'Não foi possível carregar os demonstrativos.'}</p>;
  } else if (total === 0) {
    body = <p className="text-sm text-gray-400">{company ? 'Nenhum balanço importado para esta companhia.' : 'Nenhum informe mensal da CVM para este fundo.'}</p>;
  } else if (company) {
    const cols = [...quarters.data!].reverse().slice(0, all ? undefined : limit);
    body = <Table columns={cols} labels={cols.map(c => quarterLabel(c.periodEnd))}
                  rows={[...COMPANY_FLOW(ttm), ...COMPANY_POSITION]} />;
  } else {
    const cols = [...months.data!].reverse().slice(0, all ? undefined : limit);
    body = <Table columns={cols} labels={cols.map(c => monthLabel(c.referenceMonth))} rows={FII_ROWS} />;
  }

  return (
    <Card>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h3 className="text-sm font-medium text-gray-400">
          {company ? 'Demonstrativos (CVM — DFP/ITR)' : 'Informe mensal (CVM)'}
        </h3>
        <div className="flex flex-wrap items-center gap-2">
          {company && (
            <Toggle label="Base" value={ttm ? 'ttm' : 'q'} onChange={v => setTtm(v === 'ttm')}
                    options={[{ value: 'q', label: 'Trimestre' }, { value: 'ttm', label: '12 meses' }]} />
          )}
          {total > limit && (
            <Toggle label="Quantidade" value={all ? 'all' : 'last'} onChange={v => setAll(v === 'all')}
                    options={[{ value: 'last', label: company ? `Últimos ${limit}` : `Últimos ${limit}` }, { value: 'all', label: `Todos (${total})` }]} />
          )}
        </div>
      </div>
      {body}
      {company && total > 0 && (
        <p className="mt-3 text-xs text-gray-500">
          Receita a dividendos: {ttm ? 'soma de 12 meses' : 'trimestre isolado'}. PL, ativo, caixa, dívida e ações: posição na data do balanço.
          Passe o mouse para ver o valor exato.
        </p>
      )}
    </Card>
  );
};
