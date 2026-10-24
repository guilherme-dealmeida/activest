import { useSearchParams } from 'react-router-dom';
import { clsx } from 'clsx';
import { IssuerListingTable } from '@/components/issuer/IssuerListingTable';
import { IssuerSearch } from '@/components/issuer/IssuerSearch';
import { ListingTab, TAB_LABEL } from '@/utils/issuerListing';

const TABS: ListingTab[] = ['stocks', 'fiis'];

/**
 * Listagem de ativos (antes "Emissoras", renomeada em 18/10/2026) no lugar do aviso vazio de /ativos (docs/planos/PLANO_LISTAGEM_EMISSORAS.md, 5.1):
 * abas Ações e FIIs, no estilo das abas de Dividendos. A aba fica na URL (`?tab=fiis`); cada aba guarda os seus
 * filtros, ordem e página nos parâmetros com o prefixo dela.
 */
export const IssuerListing = ({ onOpen }: { onOpen: (ticker: string) => void }) => {
  const [params, setParams] = useSearchParams();
  const tab: ListingTab = params.get('tab') === 'fiis' ? 'fiis' : 'stocks';
  const setTab = (t: ListingTab) => {
    const next = new URLSearchParams(params);
    if (t === 'fiis') next.set('tab', 'fiis'); else next.delete('tab');
    setParams(next, { replace: true });
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex space-x-1 bg-surface p-1 rounded-lg w-fit border border-surface-light" role="tablist">
          {TABS.map(t => (
            <button
              key={t}
              role="tab"
              aria-selected={tab === t}
              onClick={() => setTab(t)}
              className={clsx(
                'px-4 py-2 text-sm font-medium rounded-md transition-colors',
                tab === t ? 'bg-surface-light text-white' : 'text-gray-400 hover:text-white hover:bg-surface-light/50'
              )}
            >
              {TAB_LABEL[t]}
            </button>
          ))}
        </div>
        {/* Busca do ativo no lugar da de cima, com o "+" e o comportamento da de Preço Teto */}
        <div className="w-full max-w-md">
          <IssuerSearch onSelect={onOpen} addLabel="Abrir ativo" />
        </div>
      </div>
      <IssuerListingTable key={tab} tab={tab} />
    </div>
  );
};
