import { ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Clock, ExternalLink } from 'lucide-react';
import { indicatorsApi } from '@/api/indicators';
import { Card } from '@/components/ui/Card';
import { InfoTooltip } from '@/components/ui/InfoTooltip';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { CompanyData as CompanyDataValues, Issuer } from '@/types';
import { MONTHS, formatCnpj, formatCompactCurrency, formatCompactNumber } from '@/utils/indicators';
import { formatDate } from '@/utils/format';
import { apiMessage } from '@/utils/apiError';

/** Link externo só com http(s): o site vem da CVM, digitado pela companhia. */
const siteUrl = (site: string | null): string | null => {
  if (!site) return null;
  const s = site.trim();
  const url = /^https?:\/\//i.test(s) ? s : `https://${s}`;
  try {
    const u = new URL(url);
    return u.protocol === 'http:' || u.protocol === 'https:' ? u.toString() : null;
  } catch {
    return null;
  }
};

const exact = (v: number) => v.toLocaleString('pt-BR', { maximumFractionDigits: 2 });

const Field = ({ label, children }: { label: string; children: ReactNode }) => (
  <div className="min-w-0">
    <dt className="text-xs uppercase tracking-wide text-gray-500">{label}</dt>
    <dd className="mt-0.5 text-sm text-white break-words">{children}</dd>
  </div>
);

/** Um valor do quadro (anexo 4): rótulo com a fórmula no (i), valor simples e o exato no hover. */
interface ValueItem {
  label: string;
  formula: string;
  value: number | null | undefined;
  kind: 'money' | 'count';
  /** Nota do valor (classe avaliada pelo preço da outra, motivo do vazio). */
  note?: string | null;
}

const ValueCell = ({ item }: { item: ValueItem }) => {
  const v = item.value;
  const text = v == null ? '—' : item.kind === 'money' ? formatCompactCurrency(v) : formatCompactNumber(v);
  const title = v == null ? undefined : item.kind === 'money' ? `R$ ${exact(v)}` : exact(v);
  return (
    <div className="min-w-0 rounded-lg bg-white/[0.03] px-3 py-2">
      <dt className="flex items-center gap-1 text-xs uppercase tracking-wide text-gray-500">
        {item.label}
        <InfoTooltip label={`O que é ${item.label}`} content={item.formula} />
      </dt>
      <dd className="mt-0.5 flex items-center gap-1.5">
        <span title={title} className={`text-base font-semibold tabular-nums ${v != null && v < 0 ? 'text-danger' : 'text-white'}`}>{text}</span>
        {item.note && <InfoTooltip label="Observação" content={item.note} />}
      </dd>
    </div>
  );
};

const company = (d: CompanyDataValues): ValueItem[] => {
  const bank = d.statementLayout === 'BANCO' || d.statementLayout === 'SEGURADORA';
  const items: ValueItem[] = [
    { label: 'Valor de mercado', value: d.marketValue, kind: 'money', note: d.marketValueNote,
      formula: 'Ações ON e PN sem tesouraria × o preço atual de cada classe (classe sem cotação: o preço da outra, com aviso). Unit não entra: as ações que a compõem já entram.' },
    ...(bank ? [] : [{ label: 'Valor de firma', value: d.enterpriseValue, kind: 'money' as const,
      formula: 'Valor de mercado + dívida líquida (dívida bruta − disponibilidade).' }]),
    { label: 'Patrimônio líquido', value: d.equity, kind: 'money', formula: 'PL da controladora no último balanço.' },
    { label: 'Nº total de papéis', value: d.sharesTotal, kind: 'count',
      formula: 'Total de ações da companhia (com tesouraria) no último balanço, ajustado por desdobramentos, grupamentos e bonificações posteriores.' },
    { label: 'Ativos', value: d.totalAssets, kind: 'money', formula: 'Ativo total do último balanço.' },
  ];
  if (!bank) {
    items.push(
      { label: 'Ativo circulante', value: d.currentAssets, kind: 'money', formula: 'Ativo circulante do último balanço.' },
      { label: 'Dívida bruta', value: d.grossDebt, kind: 'money', formula: 'Empréstimos e financiamentos de curto e longo prazo.' },
      { label: 'Dívida líquida', value: d.netDebt, kind: 'money', formula: 'Dívida bruta − disponibilidade.' },
      { label: 'Disponibilidade', value: d.cash, kind: 'money', formula: 'Caixa e equivalentes + aplicações financeiras.' },
    );
  }
  items.push(liquidity(d));
  return items;
};

const fund = (d: CompanyDataValues): ValueItem[] => [
  { label: 'Valor de mercado', value: d.marketValue, kind: 'money', note: d.marketValueNote,
    formula: 'Cotas emitidas do último informe × preço atual da cota.' },
  { label: 'Patrimônio líquido', value: d.equity, kind: 'money', formula: 'PL do último informe mensal da CVM.' },
  { label: 'Nº de cotas', value: d.sharesTotal, kind: 'count', formula: 'Cotas emitidas do último informe mensal.' },
  { label: 'Ativos', value: d.totalAssets, kind: 'money', formula: 'Ativo total do último informe mensal.' },
  liquidity(d),
];

const liquidity = (d: CompanyDataValues): ValueItem => ({
  label: 'Liquidez média diária', value: d.averageDailyLiquidity, kind: 'money',
  note: d.averageDailyLiquidity == null ? `Sem pregão com volume nos últimos ${d.liquidityMonths} meses.` : null,
  formula: `Média de quantidade negociada × fechamento do ticker nos pregões dos últimos ${d.liquidityMonths} meses (${d.liquiditySessions} pregões). Aproxima o volume financeiro: a B3 publica a quantidade, não o volume.`,
});

/** Relógio de "dado antigo", com o motivo no hover (mesmos limites da listagem, D5). */
const StaleMark = ({ title }: { title: string }) => (
  <span title={title} aria-label={title} className="inline-flex cursor-help text-accent">
    <Clock className="h-3.5 w-3.5" />
  </span>
);

const monthOf = (iso: string) => `${MONTHS[Number(iso.slice(5, 7)) - 1]}/${iso.slice(0, 4)}`;

/**
 * Card "Dados da empresa" (ação) / "Dados do fundo" (FII) — antigo "Cadastro" (docs/planos/PLANO_TELA_ATIVOS.md, T2,
 * D1–D7, D11): o quadro de valores que a base já tem (anexo 4) e, embaixo, os campos do cadastro de sempre. Só valor
 * simples, com o exato no hover (D11). Banco e seguradora sem valor de firma, circulante, dívidas e disponibilidade
 * (não têm no formato comercial).
 */
export const CompanyData = ({ ticker, issuer }: { ticker: string; issuer: Issuer }) => {
  const isFund = issuer.kind === 'FII';
  const data = useQuery({
    queryKey: ['company-data', ticker],
    queryFn: () => indicatorsApi.companyData(ticker),
    staleTime: 5 * 60_000,
  });
  const site = siteUrl(issuer.website);
  const sector = [issuer.b3Sector, issuer.b3Subsector, issuer.b3Segment].filter(Boolean).join(' › ');
  const status = issuer.registrationStatus;

  let values: ReactNode;
  if (data.isLoading) {
    values = <div className="flex h-24 items-center justify-center"><LoadingSpinner className="h-5 w-5 text-accent" /></div>;
  } else if (data.isError || !data.data) {
    values = <p className="text-sm text-gray-400">{apiMessage(data.error) ?? 'Não foi possível carregar os valores.'}</p>;
  } else {
    const d = data.data;
    const items = isFund ? fund(d) : company(d);
    const base = d.basePeriodEnd ? (isFund ? `Informe de ${monthOf(d.basePeriodEnd)}` : `Balanço de ${formatDate(d.basePeriodEnd)}`) : null;
    const price = d.priceDate ? `preço de ${formatDate(d.priceDate)}` : null;
    values = (
      <>
        <dl className="grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
          {items.map(i => <ValueCell key={i.label} item={i} />)}
        </dl>
        {(base || price) && (
          <p className="mt-2 flex flex-wrap items-center gap-1.5 text-xs text-gray-500">
            {base}
            {d.baseStale && base && <StaleMark title={`${base}: dado antigo`} />}
            {base && price && ' · '}
            {price}
            {d.priceStale && price && <StaleMark title={`Último pregão em ${formatDate(d.priceDate!)}`} />}
          </p>
        )}
      </>
    );
  }

  return (
    <Card title={isFund ? 'Dados do fundo' : 'Dados da empresa'}>
      {values}
      <dl className="mt-5 grid grid-cols-1 gap-4 border-t border-white/5 pt-4 sm:grid-cols-2 lg:grid-cols-3">
        <Field label="CNPJ">{formatCnpj(issuer.cnpj)}</Field>
        {issuer.cvmCode != null && <Field label="Código CVM">{issuer.cvmCode}</Field>}
        {status && (
          <Field label="Situação do registro">
            <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${
              status.toUpperCase() === 'ATIVO' ? 'bg-success/15 text-success' : 'bg-danger/15 text-danger'
            }`}>
              {status}
            </span>
          </Field>
        )}
        {site && (
          <Field label="Site">
            <a href={site} target="_blank" rel="noopener noreferrer"
               className="inline-flex items-center gap-1 text-accent hover:underline break-all">
              {issuer.website}
              <ExternalLink className="h-3.5 w-3.5 shrink-0" />
            </a>
          </Field>
        )}
        {sector && <Field label="Setor B3">{sector}</Field>}
        {issuer.segment && <Field label="Segmento">{issuer.segment}</Field>}
        {issuer.listingSegment && <Field label="Segmento de listagem">{issuer.listingSegment}</Field>}
        {issuer.fiscalYearEndMonth && (
          <Field label="Fim do exercício">{MONTHS[issuer.fiscalYearEndMonth - 1]}</Field>
        )}
        {issuer.administratorName && <Field label="Administrador">{issuer.administratorName}</Field>}
      </dl>
    </Card>
  );
};
