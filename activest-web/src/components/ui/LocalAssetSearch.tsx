import { useEffect, useMemo, useRef, useState } from 'react';
import { Search } from 'lucide-react';

export interface LocalAssetOption {
  ticker: string;
  /** Nome do ativo (razão social, nome do fundo...). */
  name: string;
  /** Descrição do tipo de ativo (Ações, FIIs...). */
  typeLabel: string;
}

interface Props {
  /** Ativos que a tela tem para mostrar (os da própria listagem). */
  options: LocalAssetOption[];
  /** Chamado com o ticker escolhido (lista ou Enter). */
  onSelect: (ticker: string) => void;
  /** Rótulo de acessibilidade do campo. */
  ariaLabel?: string;
}

/** Sem acento e em maiúsculas: "Itaú" acha "ITAU UNIBANCO". */
const normalizeText = (s: string) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toUpperCase();

/**
 * Busca por ticker ou nome sobre a listagem da própria tela (Dividendos e
 * Transações), com o visual e o teclado da busca da tela Ativos (`IssuerSearch`).
 * A diferença é a fonte: aqui as opções vêm da tela, no navegador — um ativo
 * fora da listagem deixaria a tabela vazia. Nas duas telas escolher um ativo
 * tem o efeito do ícone Listar.
 */
export const LocalAssetSearch = ({ options, onSelect, ariaLabel = 'Buscar ativo por ticker ou nome' }: Props) => {
  const [value, setValue] = useState('');
  const [open, setOpen] = useState(false);
  const [highlight, setHighlight] = useState(-1);
  const boxRef = useRef<HTMLDivElement>(null);

  // A partir de 2 caracteres, como na tela Ativos. Ticker que começa com o texto vem primeiro.
  const matches = useMemo(() => {
    const q = normalizeText(value.trim());
    if (q.length < 2) return [];
    return options
      .filter(o => normalizeText(o.ticker).includes(q) || normalizeText(o.name).includes(q))
      .sort((a, b) => {
        const pa = normalizeText(a.ticker).startsWith(q) ? 0 : 1;
        const pb = normalizeText(b.ticker).startsWith(q) ? 0 : 1;
        return pa - pb || a.ticker.localeCompare(b.ticker, 'pt-BR');
      });
  }, [options, value]);

  useEffect(() => {
    setOpen(matches.length > 0);
    setHighlight(-1);
  }, [matches]);

  useEffect(() => {
    const onClickOutside = (e: MouseEvent) => {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onClickOutside);
    return () => document.removeEventListener('mousedown', onClickOutside);
  }, []);

  const go = (ticker: string) => {
    setValue('');
    setOpen(false);
    onSelect(ticker);
  };

  // Enter: a opção destacada; sem destaque, o ticker digitado exato ou a primeira da lista.
  const submit = () => {
    if (matches.length === 0) return;
    const typed = normalizeText(value.trim());
    const choice = highlight >= 0
      ? matches[highlight]
      : matches.find(o => normalizeText(o.ticker) === typed) ?? matches[0];
    go(choice.ticker);
  };

  return (
    <div ref={boxRef} className="relative w-full max-w-md">
      <div className="relative">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
        <input
          type="text"
          autoComplete="off"
          aria-label={ariaLabel}
          value={value}
          onChange={(e) => setValue(e.target.value.toUpperCase())}
          onFocus={() => setOpen(matches.length > 0)}
          onKeyDown={(e) => {
            if (e.key === 'ArrowDown' && open) { e.preventDefault(); setHighlight(p => Math.min(p + 1, matches.length - 1)); }
            else if (e.key === 'ArrowUp' && open) { e.preventDefault(); setHighlight(p => Math.max(p - 1, 0)); }
            else if (e.key === 'Enter') { e.preventDefault(); submit(); }
            else if (e.key === 'Escape') setOpen(false);
          }}
          placeholder="Buscar ticker ou nome (ex: PETR4, HGLG11, Itaú)"
          className="flex h-10 w-full rounded-md border border-surface-light bg-surface pl-9 pr-3 py-2 text-sm text-white focus:ring-2 focus:ring-accent focus:border-transparent"
        />
      </div>

      {open && matches.length > 0 && (
        <div className="absolute z-40 w-full mt-1 max-h-72 overflow-y-auto rounded-md border border-white/10 bg-surface shadow-xl">
          {matches.map((opt, idx) => (
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
              <span className="text-xs text-gray-500">{opt.typeLabel}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
};
