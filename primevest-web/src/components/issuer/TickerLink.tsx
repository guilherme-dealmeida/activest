import { Link } from 'react-router-dom';

interface Props {
  ticker: string;
  /** assets.issuer_id — com emissor, o ticker leva à tela de emissoras. */
  issuerId: number | null | undefined;
  className?: string;
}

/**
 * Ticker clicável para /emissoras/{ticker} (docs/planos/PLANO_TELA_EMISSORES.md, 2.8, B6).
 * Sem emissor (renda fixa, ETF, BDR, cripto), fica como texto. O clique não
 * sobe para a linha da tabela.
 */
export const TickerLink = ({ ticker, issuerId, className = 'font-medium' }: Props) => {
  if (issuerId == null || issuerId === undefined) {
    return <span className={className}>{ticker}</span>;
  }
  return (
    <Link
      to={`/emissoras/${encodeURIComponent(ticker)}`}
      onClick={(e) => e.stopPropagation()}
      title="Ver emissora e indicadores"
      className={`${className} hover:text-accent hover:underline underline-offset-2 transition-colors`}
    >
      {ticker}
    </Link>
  );
};
