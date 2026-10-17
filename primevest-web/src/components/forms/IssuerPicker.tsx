import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ChevronDown, X } from 'lucide-react';
import { fixedIncomeIssuersApi } from '@/api/fixedIncomeIssuers';
import { LabelWithInfo } from '@/components/ui/InfoTooltip';
import { IssuerPick, pickOf } from '@/components/forms/issuerPick';

/** Situação da empresa morta, em cinza ao lado do nome. */
const STATUS_LABEL: Record<string, string> = {
  INATIVA: 'Inativa',
  CANCELADA: 'Cancelada',
  LIQUIDACAO_EXTRAJUDICIAL: 'Em liquidação',
  LIQUIDACAO_ORDINARIA: 'Em liquidação',
  FALENCIA: 'Falência',
};
const situacao = (o: { dead?: boolean; status?: string | null }) =>
  o.dead ? (STATUS_LABEL[o.status ?? ''] ?? 'Inativa') : null;

interface Props {
  label: string;
  info?: string;
  role: 'ISSUER' | 'DISTRIBUTOR';
  /** Emissora: o produto (CDB) decide os tipos aceitos. */
  ticker?: string | null;
  value: IssuerPick | null;
  onChange: (pick: IssuerPick | null) => void;
  disabled?: boolean;
  /**
   * Lista fechada no lugar da busca: a venda do Tesouro só mostra as distribuidoras com título aberto do ativo (D7).
   */
  options?: IssuerPick[];
}

/**
 * Seletor com busca de emissora ou distribuidora (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, seção 7): digita-se o
 * nome do grupo, a razão social ou o CNPJ; a lista mostra "Grupo — razão social", a empresa morta com a situação em
 * cinza e "Instituição não listada" por último. Sem texto livre: só vale o que está no cadastro.
 */
export const IssuerPicker = ({ label, info, role, ticker, value, onChange, disabled, options }: Props) => {
  const [text, setText] = useState('');
  const [open, setOpen] = useState(false);
  const [q, setQ] = useState('');
  const [highlight, setHighlight] = useState(-1);
  const boxRef = useRef<HTMLDivElement>(null);

  // Espera parar de digitar para buscar
  useEffect(() => {
    const t = setTimeout(() => setQ(text.trim()), 250);
    return () => clearTimeout(t);
  }, [text]);

  const { data: found, isFetching } = useQuery({
    queryKey: ['fixed-income-issuers', role, ticker ?? null, q],
    queryFn: () => fixedIncomeIssuersApi.search(role, q, ticker),
    enabled: open && !options && (role === 'DISTRIBUTOR' || !!ticker),
    staleTime: 5 * 60 * 1000,
  });

  const lista: IssuerPick[] = options
    ? options.filter(o => !q || o.label.toLowerCase().includes(q.toLowerCase()))
    : (found ?? []).map(pickOf);

  useEffect(() => { setHighlight(-1); }, [q, open]);

  useEffect(() => {
    const fora = (e: MouseEvent) => {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) {
        setOpen(false);
        setText('');
      }
    };
    document.addEventListener('mousedown', fora);
    return () => document.removeEventListener('mousedown', fora);
  }, []);

  const escolher = (p: IssuerPick) => {
    onChange(p);
    setOpen(false);
    setText('');
  };

  const valor = open ? text : (value?.label ?? '');
  const morta = value ? situacao(value) : null;

  return (
    <div ref={boxRef} className="relative w-full">
      <label className="block text-sm font-medium text-gray-300 mb-1.5">
        <LabelWithInfo info={info}>{label}</LabelWithInfo>
      </label>
      <div className="relative">
        <input
          type="text"
          autoComplete="off"
          aria-label={label}
          disabled={disabled}
          value={valor}
          placeholder={value ? value.label : (options ? 'Escolha' : 'Digite o nome ou o CNPJ')}
          title={value?.label}
          onFocus={() => { if (!disabled) setOpen(true); }}
          onChange={(e) => { setText(e.target.value); setOpen(true); }}
          onKeyDown={(e) => {
            if (e.key === 'ArrowDown') { e.preventDefault(); setOpen(true); setHighlight(p => Math.min(p + 1, lista.length - 1)); }
            else if (e.key === 'ArrowUp') { e.preventDefault(); setHighlight(p => Math.max(p - 1, 0)); }
            else if (e.key === 'Enter') {
              if (open && lista.length > 0) { e.preventDefault(); escolher(lista[Math.max(highlight, 0)]); }
            } else if (e.key === 'Escape') { setOpen(false); setText(''); }
          }}
          className={`flex h-10 w-full rounded-md border border-surface-light bg-surface pl-3 pr-14 py-2 text-sm placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:ring-accent focus:border-transparent disabled:cursor-not-allowed disabled:opacity-50 ${
            !open && morta ? 'text-gray-400' : 'text-white'}`}
        />
        <div className="absolute right-2 top-1/2 -translate-y-1/2 flex items-center gap-1 text-gray-500">
          {!open && morta && <span className="text-[10px] uppercase">{morta}</span>}
          {value && !disabled && !options && (
            <button type="button" aria-label={`Limpar ${label}`} onClick={() => onChange(null)} className="hover:text-gray-300">
              <X className="w-3.5 h-3.5" />
            </button>
          )}
          <ChevronDown className="w-4 h-4" />
        </div>
      </div>

      {open && !disabled && (
        <div className="absolute z-40 w-full mt-1 max-h-72 overflow-y-auto rounded-md border border-white/10 bg-surface shadow-xl">
          {lista.length === 0 ? (
            <p className="px-3 py-2.5 text-sm text-gray-500">
              {isFetching ? 'Buscando…' : role === 'ISSUER' && !ticker ? 'Escolha o ativo primeiro' : 'Nenhuma empresa encontrada'}
            </p>
          ) : lista.map((o, idx) => {
            const s = situacao(o);
            return (
              <button
                key={o.id}
                title={o.label}
                type="button"
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => escolher(o)}
                className={`w-full text-left px-3 py-2 flex items-center gap-2 text-sm border-b border-white/5 last:border-0 transition-colors ${
                  idx === highlight ? 'bg-accent/15' : 'hover:bg-white/5'} ${s ? 'text-gray-500' : 'text-gray-200'}`}
              >
                <span className="truncate flex-1">{o.label}</span>
                {s && <span className="text-[10px] uppercase text-gray-500">{s}</span>}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
};
