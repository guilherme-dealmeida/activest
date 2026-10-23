import { Link } from 'react-router-dom';

interface Props {
  ticker: string;
  /** assets.issuer_id — com emissor, o ticker leva à tela Ativos. */
  issuerId: number | null | undefined;
  className?: string;
}

/**
 * Ticker clicável para /ativos/{ticker} (tela Ativos, antes Emissoras) (docs/planos/PLANO_TELA_EMISSORES.md, 2.8, B6).
 * Sem emissor (renda fixa, ETF, BDR, cripto), fica como texto. O clique não
 * sobe para a linha da tabela.
 */
export const TickerLink = ({ ticker, issuerId, className = 'font-medium' }: Props) => {
  if (issuerId == null || issuerId === undefined) {
    return <span className={className}>{ticker}</span>;
  }
  return (
    <Link
      to={`/assets/${encodeURIComponent(ticker)}`}
      onClick={(e) => e.stopPropagation()}
      title="Ver ativo e indicadores"
      className={`${className} hover:text-accent hover:underline underline-offset-2 transition-colors`}
    >
      {ticker}
    </Link>
  );
};
