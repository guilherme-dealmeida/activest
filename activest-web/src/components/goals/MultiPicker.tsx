import { useEffect, useMemo, useRef, useState } from 'react';
import { Search, X } from 'lucide-react';
import { Option } from '@/types/goals';
import { assetText } from '@/utils/assetLabel';

const norm = (s: string) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();

/**
 * Escolha de vários itens de uma lista que pode ser longa (ativos, categorias, emissores): campo de busca com a lista
 * filtrada embaixo (altura limitada) e os escolhidos como etiquetas. Ocupa o mesmo espaço com 5 ou 500 opções.
 */
export const MultiPicker = ({ options, value, onChange, placeholder = 'Buscar…', emptyText = 'Nenhum item' }: {
  options: Option[]; value: string[]; onChange: (v: string[]) => void; placeholder?: string; emptyText?: string;
}) => {
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState(false);
  const box = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const close = (e: MouseEvent) => { if (box.current && !box.current.contains(e.target as Node)) setOpen(false); };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);

  // fundo: o nome curto no lugar do ticker (CNPJ) — PLANO_FUNDOS_INVESTIMENTO.md, D3
  const label = useMemo(() => new Map(options.map(o => [o.value, assetText(o.label)])), [options]);
  const filtered = useMemo(() => {
    const q = norm(query.trim());
    return options.filter(o => !value.includes(o.value) && (!q || norm(assetText(o.label)).includes(q) || norm(o.value).includes(q)));
  }, [options, query, value]);

  return (
    <div ref={box} className="relative">
      <div className="flex h-10 items-center gap-2 rounded-control border border-line bg-surface px-3 focus-within:ring-2 focus-within:ring-accent">
        <Search className="h-4 w-4 shrink-0 text-content-4" />
        <input value={query} placeholder={placeholder} onFocus={() => setOpen(true)}
          onChange={e => { setQuery(e.target.value); setOpen(true); }}
          onKeyDown={e => {
            if (e.key === 'Enter' && filtered[0]) { e.preventDefault(); onChange([...value, filtered[0].value]); setQuery(''); }
            if (e.key === 'Escape') setOpen(false);
          }}
          className="h-full w-full bg-transparent text-sm text-content placeholder:text-content-4 focus:outline-none" />
      </div>
      {open && (
        <ul className="absolute z-20 mt-1 max-h-52 w-full overflow-y-auto rounded-control border border-line bg-surface py-1 shadow-lg">
          {filtered.length === 0 && <li className="px-3 py-2 text-xs text-content-4">{options.length === 0 ? emptyText : 'Nada encontrado'}</li>}
          {filtered.map(o => (
            <li key={o.value}>
              <button type="button" onClick={() => { onChange([...value, o.value]); setQuery(''); }}
                className="w-full px-3 py-1.5 text-left text-sm text-content-2 hover:bg-elevated hover:text-content">
                {assetText(o.label)}
              </button>
            </li>
          ))}
        </ul>
      )}
      {value.length > 0 && (
        <div className="mt-2 flex flex-wrap gap-1.5">
          {value.map(v => (
            <span key={v} className="inline-flex items-center gap-1 rounded-full bg-elevated px-2.5 py-0.5 text-xs text-content">
              {label.get(v) ?? v}
              <button type="button" aria-label={`Tirar ${label.get(v) ?? v}`} onClick={() => onChange(value.filter(x => x !== v))}
                className="text-content-4 hover:text-content"><X className="h-3 w-3" /></button>
            </span>
          ))}
        </div>
      )}
    </div>
  );
};
