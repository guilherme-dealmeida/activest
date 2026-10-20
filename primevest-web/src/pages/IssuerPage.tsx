import { ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { indicatorsApi } from '@/api/indicators';
import { issuersApi } from '@/api/issuers';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { IssuerSearch } from '@/components/issuer/IssuerSearch';
import { IssuerHeader } from '@/components/issuer/IssuerHeader';
import { PriceChart } from '@/components/issuer/PriceChart';
import { IndicatorGrid } from '@/components/issuer/IndicatorGrid';
import { IssuerRegistry } from '@/components/issuer/IssuerRegistry';
import { FinancialStatements } from '@/components/issuer/FinancialStatements';
import { IssuerListing } from '@/components/issuer/IssuerListing';
import { apiMessage } from '@/utils/apiError';

const status = (error: unknown): number | undefined =>
  (error as { response?: { status?: number } } | null)?.response?.status;

/**
 * Tela de Emissoras (docs/planos/PLANO_TELA_EMISSORES.md): sem ticker, a listagem do catálogo em abas
 * (docs/planos/PLANO_LISTAGEM_EMISSORAS.md); a partir de um ticker, o
 * emissor (companhia ou FII), a cotação, os indicadores com histórico, o
 * cadastro e os demonstrativos. A URL é o estado: /ativos/PETR4?periodo=5A. A tela se chamava Emissoras até
 * 18/10/2026 (/emissoras redireciona para /ativos).
 */
export const IssuerPage = () => {
  const { ticker: raw } = useParams();
  const ticker = raw?.trim().toUpperCase();
  const navigate = useNavigate();
  const location = useLocation();

  const current = useQuery({
    queryKey: ['indicators', ticker],
    queryFn: () => indicatorsApi.current(ticker!),
    enabled: !!ticker,
    retry: (count, err) => status(err) !== 404 && status(err) !== 400 && count < 2,
  });
  const issuerId = current.data?.issuerId;
  const issuer = useQuery({
    queryKey: ['issuer', issuerId],
    queryFn: () => issuersApi.get(issuerId!),
    enabled: !!issuerId,
    staleTime: 10 * 60_000,
  });

  // Leva só o período da cotação para a emissora: os filtros da listagem (docs/planos/PLANO_LISTAGEM_EMISSORAS.md,
  // 5.4) ficam na URL da lista, e o "voltar" do navegador os devolve.
  const goTo = (t: string) => {
    const from = new URLSearchParams(location.search);
    const keep = new URLSearchParams();
    for (const k of ['periodo', 'de', 'ate']) {
      const v = from.get(k);
      if (v) keep.set(k, v);
    }
    const q = keep.toString();
    navigate(`/ativos/${encodeURIComponent(t)}${q ? `?${q}` : ''}`);
  };

  let content: ReactNode = null;
  if (!ticker) {
    // Sem ticker: a listagem de ativos (Ações e FIIs) no lugar do aviso vazio, com a busca ao lado das abas
    content = <IssuerListing onOpen={goTo} />;
  } else if (current.isLoading) {
    content = <div className="flex h-64 items-center justify-center"><LoadingSpinner className="h-8 w-8 text-accent" /></div>;
  } else if (current.isError) {
    const msg = apiMessage(current.error) ?? '';
    const semEmissor = /sem emissor/i.test(msg);
    const naoEncontrado = status(current.error) === 404 && !semEmissor;
    content = (
      <div className="rounded-xl border border-white/10 bg-surface p-8 text-center">
        <p className="text-white font-medium">
          {naoEncontrado ? `Não achamos o ticker ${ticker}.`
            : semEmissor ? `${ticker} não tem emissor cadastrado.`
            : 'Não foi possível carregar o ativo.'}
        </p>
        <p className="mt-1 text-sm text-gray-400">
          {naoEncontrado ? 'Confira o código ou use a busca acima.'
            : semEmissor ? 'Indicadores e demonstrativos existem só para ações e FIIs ligados a um emissor (ETF, BDR, Tesouro, renda fixa e cripto ficam de fora).'
            : msg}
        </p>
      </div>
    );
  } else if (current.data) {
    content = (
      <>
        <IssuerHeader ticker={current.data.ticker} issuer={issuer.data} current={current.data} />
        <PriceChart ticker={current.data.ticker} />
        <IndicatorGrid key={current.data.ticker} current={current.data} />
        {issuer.data && <IssuerRegistry issuer={issuer.data} />}
        {issuer.data && <FinancialStatements key={issuer.data.id} issuer={issuer.data} />}
        {issuer.isError && (
          <p className="text-sm text-gray-400">{apiMessage(issuer.error) ?? 'Não foi possível carregar o cadastro do emissor.'}</p>
        )}
      </>
    );
  }

  return (
    <div className="space-y-6">
      {/* Na listagem a busca fica ao lado das abas (com o "+", como em Preço Teto); na emissora, no topo. */}
      {ticker && <IssuerSearch onSelect={goTo} />}
      {content}
    </div>
  );
};
