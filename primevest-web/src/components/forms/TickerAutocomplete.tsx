import React, { useEffect, useRef, useState } from 'react';
import { Search } from 'lucide-react';
import { assetsApi } from '@/api/assets';
import { Asset } from '@/types';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';

interface Props {
  value: string;
  typeId: number | null;
  disabled?: boolean;
  /** Chamado a cada tecla. asset vem preenchido quando o ticker casa com um existente. */
  onChange: (ticker: string, asset: Asset | null) => void;
}

/**
 * Autocomplete de ticker restrito a um tipo de ativo.
 *
 * O tipo é quem manda: sem tipo escolhido o campo fica desabilitado, e a busca
 * sempre passa typeId para a API. Quando o usuário escolhe uma sugestão, o
 * ativo inteiro sobe para o formulário — é assim que o pai sabe se deve tratar
 * o cadastro como ativo existente ou novo.
 */
export const TickerAutocomplete = ({ value, typeId, disabled, onChange }: Props) => {
  const [options, setOptions] = useState<Asset[]>([]);
  const [open, setOpen] = useState(false);
  const [searching, setSearching] = useState(false);
  const [highlight, setHighlight] = useState(-1);
  const boxRef = useRef<HTMLDivElement>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (disabled || !typeId || value.trim().length < 2) {
      setOptions([]); setOpen(false); return;
    }
    setSearching(true);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(async () => {
      try {
        const results = await assetsApi.getAll(typeId, value.trim());
        const limited = (results as Asset[]).slice(0, 15);
        setOptions(limited);
        setOpen(limited.length > 0);
        setHighlight(-1);
      } catch {
        setOptions([]); setOpen(false);
      } finally {
        setSearching(false);
      }
    }, 300);
    return () => { if (debounceRef.current) clearTimeout(debounceRef.current); };
  }, [value, typeId, disabled]);

  useEffect(() => {
    const onClickOutside = (e: MouseEvent) => {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onClickOutside);
    return () => document.removeEventListener('mousedown', onClickOutside);
  }, []);

  const pick = (asset: Asset) => {
    onChange(asset.ticker, asset);
    setOpen(false);
    setOptions([]);
  };

  const semTipo = !typeId;
  const semResultado = !semTipo && !searching && value.trim().length >= 2 && options.length === 0;

  return (
    <div ref={boxRef} className="relative">
      <label className="block text-sm font-medium text-gray-300 mb-1.5">Ticker (Ativo)</label>
      <div className="relative">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
        <input
          type="text"
          autoComplete="off"
          required
          disabled={disabled || semTipo}
          value={value}
          onChange={(e) => onChange(e.target.value.toUpperCase(), null)}
          onKeyDown={(e) => {
            if (!open || options.length === 0) return;
            if (e.key === 'ArrowDown') { e.preventDefault(); setHighlight(p => Math.min(p + 1, options.length - 1)); }
            else if (e.key === 'ArrowUp') { e.preventDefault(); setHighlight(p => Math.max(p - 1, 0)); }
            else if (e.key === 'Enter' && highlight >= 0) { e.preventDefault(); pick(options[highlight]); }
            else if (e.key === 'Escape') setOpen(false);
          }}
          placeholder={semTipo ? 'Escolha o tipo de ativo primeiro' : 'Digite o ticker (ex: PETR4, CDB-BTG-2027)'}
          className="flex h-10 w-full rounded-md border border-surface-light bg-surface pl-9 pr-3 py-2 text-sm text-white focus:ring-2 focus:ring-accent focus:border-transparent disabled:opacity-60 disabled:cursor-not-allowed"
        />
        {searching && <LoadingSpinner className="absolute right-3 top-1/2 -translate-y-1/2 h-4 w-4 text-accent" />}
      </div>

      {open && options.length > 0 && (
        <div className="absolute z-50 w-full mt-1 max-h-60 overflow-y-auto rounded-md border border-white/10 bg-surface shadow-xl">
          {options.map((opt, idx) => (
            <button
              key={opt.ticker}
              type="button"
              onClick={() => pick(opt)}
              className={`w-full text-left px-3 py-2.5 flex items-center gap-3 text-sm transition-colors border-b border-white/5 last:border-0 ${
                idx === highlight ? 'bg-accent/15 text-white' : 'text-gray-300 hover:bg-white/5'
              }`}
            >
              <span className="font-semibold text-white min-w-[80px]">{opt.ticker}</span>
              <span className="truncate flex-1 text-gray-400">{opt.name}</span>
            </button>
          ))}
        </div>
      )}

      {semResultado && (
        <p className="text-xs text-accent/80 mt-1">
          Ativo novo — preencha os dados abaixo para cadastrá-lo junto com a transação.
        </p>
      )}
    </div>
  );
};
