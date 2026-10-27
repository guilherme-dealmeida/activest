import { useEffect, useRef, useState } from 'react';
import { Search } from 'lucide-react';
import { fundsApi } from '@/api/funds';
import { Asset } from '@/types';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { assetLabel, setAssetLabels } from '@/utils/assetLabel';

interface Props {
  /** Ticker escolhido (o CNPJ do fundo); vazio = nada escolhido. */
  value: string;
  typeId: number | null;
  onChange: (ticker: string, asset: Asset | null) => void;
}

const SEARCH_LIMIT = 50;

/**
 * Busca de fundo de investimento na Nova transação (docs/planos/PLANO_FUNDOS_INVESTIMENTO.md, D3, D11, D18): por nome
 * (curto, social, comercial ou antigo) ou CNPJ. A lista mostra o nome curto e, embaixo, CNPJ · categoria · marcas; os
 * encerrados ficam atrás de "Mostrar N fundos encerrados". Só vale o que foi escolhido na lista — o texto digitado não
 * vira ticker. O ticker (CNPJ sem pontuação) nunca aparece.
 */
export const FundAutocomplete = ({ value, typeId, onChange }: Props) => {
  const [text, setText] = useState(value ? assetLabel(value) : '');
  const [options, setOptions] = useState<Asset[]>([]);
  const [inactive, setInactive] = useState(0);
  const [showInactive, setShowInactive] = useState(false);
  const [open, setOpen] = useState(false);
  const [searching, setSearching] = useState(false);
  const [highlight, setHighlight] = useState(-1);
  const [chosen, setChosen] = useState<string | null>(value || null);
  const boxRef = useRef<HTMLDivElement>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (!value) { setChosen(null); return; }
    setChosen(value);
    setText(assetLabel(value));
  }, [value]);

  useEffect(() => {
    if (!typeId || chosen || text.trim().length < 2) {
      setOptions([]); setOpen(false); setInactive(0); setSearching(false); return;
    }
    setSearching(true);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(async () => {
      try {
        const r = await fundsApi.search(typeId, text.trim(), showInactive);
        setOptions(r.items);
        setInactive(r.inactive);
        setOpen(r.items.length > 0 || r.inactive > 0);
        setHighlight(-1);
      } catch {
        setOptions([]); setOpen(false); setInactive(0);
      } finally {
        setSearching(false);
      }
    }, 300);
    return () => { if (debounceRef.current) clearTimeout(debounceRef.current); };
  }, [text, typeId, chosen, showInactive]);

  useEffect(() => {
    const onClickOutside = (e: MouseEvent) => {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onClickOutside);
    return () => document.removeEventListener('mousedown', onClickOutside);
  }, []);

  const pick = (asset: Asset) => {
    const label = asset.displayName ?? asset.ticker;
    setAssetLabels({ [asset.ticker]: label });
    setChosen(asset.ticker);
    setText(label);
    setOpen(false);
    setOptions([]);
    onChange(asset.ticker, asset);
  };

  const marks = (a: Asset) => [
    a.fund?.cnpj,
    a.categoryName,
    a.fund?.subclassId ? 'Subclasse' : null,
    a.fund?.exclusive ? 'Exclusivo' : null,
    a.active ? null : 'Encerrado',
  ].filter(Boolean).join(' · ');

  const noResults = !!typeId && !searching && !chosen && text.trim().length >= 2 && options.length === 0 && inactive === 0;

  return (
    <div ref={boxRef} className="relative">
      <label className="block text-sm font-medium text-gray-300 mb-1.5">Fundo</label>
      <div className="relative">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
        <input
          type="text"
          autoComplete="off"
          required
          disabled={!typeId}
          value={text}
          title={chosen ? `CNPJ ${options.find(o => o.ticker === chosen)?.fund?.cnpj ?? chosen}` : undefined}
          onChange={(e) => {
            setChosen(null);
            setShowInactive(false);
            setText(e.target.value);
            onChange('', null);
          }}
          onKeyDown={(e) => {
            if (!open || options.length === 0) return;
            if (e.key === 'ArrowDown') { e.preventDefault(); setHighlight(p => Math.min(p + 1, options.length - 1)); }
            else if (e.key === 'ArrowUp') { e.preventDefault(); setHighlight(p => Math.max(p - 1, 0)); }
            else if (e.key === 'Enter' && highlight >= 0) { e.preventDefault(); pick(options[highlight]); }
            else if (e.key === 'Escape') setOpen(false);
          }}
          placeholder="Digite o nome ou CNPJ (ex.: CAIXA FIC PREV 300 RF, 03.926.431/0001-71)"
          className="flex h-10 w-full rounded-md border border-surface-light bg-surface pl-9 pr-3 py-2 text-sm text-white focus:ring-2 focus:ring-accent focus:border-transparent disabled:opacity-60 disabled:cursor-not-allowed"
        />
        {searching && <LoadingSpinner className="absolute right-3 top-1/2 -translate-y-1/2 h-4 w-4 text-accent" />}
      </div>

      {open && (
        <div className="absolute z-50 w-full mt-1 max-h-72 overflow-y-auto rounded-md border border-white/10 bg-surface shadow-xl">
          {options.map((opt, idx) => (
            <button
              key={opt.ticker}
              type="button"
              onClick={() => pick(opt)}
              className={`w-full text-left px-3 py-2 flex flex-col text-sm transition-colors border-b border-white/5 last:border-0 ${
                idx === highlight ? 'bg-accent/15 text-white' : 'text-gray-300 hover:bg-white/5'
              }`}
            >
              <span className={`font-semibold truncate ${opt.active ? 'text-white' : 'text-gray-400'}`}>
                {opt.displayName ?? opt.name}
              </span>
              <span className="text-xs text-gray-500 truncate">{marks(opt)}</span>
            </button>
          ))}
          {options.length >= SEARCH_LIMIT && (
            <p className="px-3 py-2 text-xs text-gray-500 border-t border-white/5">
              Mostrando os {SEARCH_LIMIT} primeiros. Refine a busca para ver o resto.
            </p>
          )}
          {!showInactive && inactive > 0 && (
            <button
              type="button"
              onClick={() => setShowInactive(true)}
              className="w-full text-left px-3 py-2 text-xs text-accent hover:bg-white/5 border-t border-white/5"
            >
              ▸ Mostrar {inactive} {inactive === 1 ? 'fundo encerrado' : 'fundos encerrados'}
            </button>
          )}
        </div>
      )}

      {noResults && (
        <p className="text-xs text-danger mt-1">Nenhum fundo encontrado com esse nome ou CNPJ.</p>
      )}
    </div>
  );
};
