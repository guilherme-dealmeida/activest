import { useEffect, useRef, useState } from 'react';
import { Plus, Search } from 'lucide-react';
import { assetsApi } from '@/api/assets';
import { Asset } from '@/types';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';

interface Props {
  /** Chamado com o ticker escolhido (lista ou Enter). */
  onSelect: (ticker: string) => void;
  /** Texto do campo vazio (padrão: o da tela de emissoras). */
  placeholder?: string;
  /**
   * Rótulo do botão "+" no fim do campo (ex: "Calcular preço teto"). Sem ele o botão não aparece.
   * O "+" faz o mesmo que o Enter: abre a opção destacada ou o ticker digitado.
   */
  addLabel?: string;
}

/**
 * Busca da tela de emissoras: só ações e FIIs ligados a emissor (B3 do
 * docs/planos/PLANO_TELA_EMISSORES.md). Mesma mecânica do TickerAutocomplete, sem tipo
 * obrigatório; Enter sem escolher na lista vai direto ao ticker digitado — a
 * própria tela avisa se ele não existe ou não tem emissor.
 */
export const IssuerSearch = ({ onSelect, placeholder = 'Buscar ticker ou nome (ex: PETR4, HGLG11, Itaú)', addLabel }: Props) => {
  const [value, setValue] = useState('');
  const [options, setOptions] = useState<Asset[]>([]);
  const [open, setOpen] = useState(false);
  const [searching, setSearching] = useState(false);
  const [highlight, setHighlight] = useState(-1);
  const boxRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    const q = value.trim();
    if (q.length < 2) {
      setOptions([]); setOpen(false); return;
    }
    setSearching(true);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(async () => {
      try {
        const results = await assetsApi.searchWithIssuer(q);
        setOptions(results);
        setOpen(results.length > 0);
        setHighlight(-1);
      } catch {
        setOptions([]); setOpen(false);
      } finally {
        setSearching(false);
      }
    }, 300);
    return () => { if (debounceRef.current) clearTimeout(debounceRef.current); };
  }, [value]);

  useEffect(() => {
    const onClickOutside = (e: MouseEvent) => {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onClickOutside);
    return () => document.removeEventListener('mousedown', onClickOutside);
  }, []);

  const go = (ticker: string) => {
    const t = ticker.trim().toUpperCase();
    if (!t) return;
    setValue('');
    setOptions([]);
    setOpen(false);
    onSelect(t);
  };

  const submit = () => {
    if (!value.trim()) { inputRef.current?.focus(); return; }
    go(open && highlight >= 0 ? options[highlight].ticker : value);
  };

  return (
    <div ref={boxRef} className="relative w-full max-w-md">
      <div className="relative">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
        <input
          ref={inputRef}
          type="text"
          autoComplete="off"
          aria-label="Buscar emissora por ticker ou nome"
          value={value}
          onChange={(e) => setValue(e.target.value.toUpperCase())}
          onKeyDown={(e) => {
            if (e.key === 'ArrowDown' && open) { e.preventDefault(); setHighlight(p => Math.min(p + 1, options.length - 1)); }
            else if (e.key === 'ArrowUp' && open) { e.preventDefault(); setHighlight(p => Math.max(p - 1, 0)); }
            else if (e.key === 'Enter') {
              e.preventDefault();
              submit();
            } else if (e.key === 'Escape') setOpen(false);
          }}
          placeholder={placeholder}
          className={`flex h-10 w-full border border-surface-light bg-surface pl-9 ${addLabel ? 'rounded-l-md rounded-r-none pr-[3.25rem]' : 'rounded-md pr-9'} py-2 text-sm text-white focus:ring-2 focus:ring-accent focus:border-transparent`}
        />
        {searching && (
          <LoadingSpinner className={`absolute ${addLabel ? 'right-14' : 'right-3'} top-1/2 -translate-y-1/2 h-4 w-4 border-2 text-accent`} />
        )}
        {addLabel && (
          <button
            type="button"
            onClick={submit}
            title={addLabel}
            aria-label={addLabel}
            className="absolute right-0 inset-y-0 w-11 flex items-center justify-center rounded-r-md bg-accent text-primary hover:bg-accent/90 transition-colors"
          >
            <Plus className="w-4 h-4" strokeWidth={2.5} />
          </button>
        )}
      </div>

      {open && options.length > 0 && (
        <div className="absolute z-40 w-full mt-1 max-h-72 overflow-y-auto rounded-md border border-white/10 bg-surface shadow-xl">
          {options.map((opt, idx) => (
            <button
              key={opt.ticker}
              type="button"
              onClick={() => go(opt.ticker)}
              className={`w-full text-left px-3 py-2.5 flex items-center gap-3 text-sm transition-colors border-b border-white/5 last:border-0 ${
                idx === highlight ? 'bg-accent/15 text-white' : 'text-gray-300 hover:bg-white/5'
              }`}
            >
              <span className="font-semibold text-white min-w-[80px]">{opt.ticker}</span>
              <span className="truncate flex-1 text-gray-400">{opt.name}</span>
              <span className="text-xs text-gray-500">{opt.typeName}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
};
