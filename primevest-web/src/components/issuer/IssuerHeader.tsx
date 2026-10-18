import { Link, useLocation } from 'react-router-dom';
import { AssetLogo } from '@/components/ui/AssetLogo';
import { AssetIndicators, Issuer } from '@/types';
import { formatCurrency, formatDate } from '@/utils/format';
import { isFractional } from '@/utils/indicators';

interface Props {
  ticker: string;
  issuer: Issuer | undefined;
  current: AssetIndicators;
}

const baseLabel = (current: AssetIndicators): string | null => {
  if (!current.basePeriodEnd) return null;
  if (current.kind === 'FII') {
    const [y, m] = current.basePeriodEnd.split('-');
    return `informe de ${m}/${y}`;
  }
  return `balanço de ${formatDate(current.basePeriodEnd)}`;
};

/**
 * Cabeçalho da emissora: logo, ticker, nomes, tipo, preço, data-base dos
 * indicadores e os outros tickers do mesmo emissor (docs/planos/PLANO_TELA_EMISSORES.md, 2.2).
 */
export const IssuerHeader = ({ ticker, issuer, current }: Props) => {
  // o chip leva o período da cotação junto
  const { search } = useLocation();
  const self = issuer?.tickers.find(t => t.ticker === ticker);
  const others = (issuer?.tickers ?? []).filter(t => t.ticker !== ticker && t.active && !isFractional(t.ticker));
  const base = baseLabel(current);

  return (
    <div className="bg-surface rounded-xl border border-surface-light p-5 flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
      <div className="flex items-start gap-4 min-w-0">
        <AssetLogo ticker={ticker} hasLogo={self?.hasLogo ?? false} size={52} />
        <div className="min-w-0">
          <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
            <h1 className="text-2xl font-bold text-white">{ticker}</h1>
            <span className="text-lg text-gray-300 truncate">{issuer?.tradeName ?? issuer?.legalName ?? ''}</span>
          </div>
          {issuer?.tradeName && issuer.legalName && issuer.legalName !== issuer.tradeName && (
            <p className="text-sm text-gray-400 truncate">{issuer.legalName}</p>
          )}
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <span className="rounded-full border border-accent/40 bg-accent/10 px-2.5 py-0.5 text-xs font-medium text-accent">
              {current.kind === 'FII' ? 'FII' : 'Empresa'}
            </span>
            {issuer?.listingSegment && (
              <span className="rounded-full border border-white/10 px-2.5 py-0.5 text-xs text-gray-300">
                {issuer.listingSegment}
              </span>
            )}
            {others.length > 0 && (
              <span className="flex flex-wrap items-center gap-1.5 text-xs text-gray-400">
                <span className="ml-1">Outros tickers:</span>
                {others.map(t => (
                  <Link
                    key={t.ticker}
                    to={`/ativos/${t.ticker}${search}`}
                    className="rounded-md border border-white/10 bg-white/5 px-2 py-0.5 font-medium text-gray-200 hover:border-accent hover:text-accent transition-colors"
                  >
                    {t.ticker}
                  </Link>
                ))}
              </span>
            )}
          </div>
        </div>
      </div>
      <div className="md:text-right shrink-0">
        <div className="text-2xl font-semibold text-white">
          {current.price != null ? formatCurrency(current.price) : '—'}
        </div>
        {base && <p className="text-xs text-gray-400 mt-1">Indicadores com base no {base}</p>}
      </div>
    </div>
  );
};
