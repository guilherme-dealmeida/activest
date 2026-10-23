import { InfoTooltip } from '@/components/ui/InfoTooltip';
import { SignedNumberInput } from '@/components/ui/SignedNumberInput';
import { INDICATOR_META } from '@/utils/indicators';
import {
  ListingRow, ListingTab, NUMERIC_COLUMNS, NumericColumn, Range, TabState, distinct,
} from '@/utils/issuerListing';

const selectClass =
  'bg-surface-light border border-white/10 text-white text-xs rounded-md focus:ring-accent focus:border-accent px-2 py-1.5 w-full';

const unitHint = (c: NumericColumn): string => {
  switch (c.unit) {
    case 'PERCENT': return 'em %';
    case 'BRL': return 'em R$';
    case 'EQUITY': return 'em R$ milhões';
    case 'COUNT': return 'quantidade';
    default: return 'em vezes';
  }
};

/**
 * Um lado da faixa: a máscara do projeto com duas casas (cotistas sem casas) e, nas colunas que podem ser negativas,
 * o botão ± do Preço Teto. Vazio = sem limite; zero é um limite de verdade.
 */
const RangeInput = ({ column, value, onChange, label, placeholder }: {
  column: NumericColumn; value: number | null; onChange: (v: number | null) => void; label: string; placeholder: string;
}) => (
  <SignedNumberInput
    value={value}
    onChange={onChange}
    onClear={() => onChange(null)}
    allowEmpty
    allowNegative={!!column.negative}
    decimals={column.unit === 'COUNT' ? 0 : 2}
    placeholder={placeholder}
    ariaLabel={label}
    size="sm"
    className="min-w-0 flex-1"
  />
);

interface Props {
  tab: ListingTab;
  rows: ListingRow[];
  state: TabState;
  onChange: (patch: Partial<TabState>) => void;
}

/**
 * Painel de filtros da aba (docs/planos/PLANO_LISTAGEM_EMISSORAS.md, 5.3): setor → subsetor → segmento (ações) ou
 * segmento (FIIs), e mín–máx de cada coluna numérica. Percentuais em pontos (DY 6 = 6%), PL em R$ milhões.
 */
export const IssuerListingFilters = ({ tab, rows, state, onChange }: Props) => {
  const sectors = distinct(rows.map(r => r.sector));
  const subsectors = distinct(rows.filter(r => !state.sector || r.sector === state.sector).map(r => r.subsector));
  const segments = distinct(rows
    .filter(r => (!state.sector || r.sector === state.sector) && (!state.subsector || r.subsector === state.subsector))
    .map(r => r.segment));

  const setRange = (key: string, side: keyof Range, v: number | null) => {
    const current = state.ranges[key] ?? { min: null, max: null };
    const next = { ...state.ranges, [key]: { ...current, [side]: v } };
    if (next[key].min === null && next[key].max === null) delete next[key];
    onChange({ ranges: next });
  };

  return (
    <div className="space-y-3 rounded-lg border border-white/10 bg-surface-light/30 p-3 text-xs">
      <div className="grid grid-cols-1 gap-x-4 gap-y-2 sm:grid-cols-3">
        {tab === 'stocks' && (
          <>
            <label className="text-gray-400">
              Setor
              <select className={`${selectClass} mt-1`} value={state.sector}
                onChange={e => onChange({ sector: e.target.value, subsector: '', segment: '' })}>
                <option value="">Todos</option>
                {sectors.map(s => <option key={s} value={s}>{s}</option>)}
              </select>
            </label>
            <label className="text-gray-400">
              Subsetor
              <select className={`${selectClass} mt-1`} value={state.subsector}
                onChange={e => onChange({ subsector: e.target.value, segment: '' })}>
                <option value="">Todos</option>
                {subsectors.map(s => <option key={s} value={s}>{s}</option>)}
              </select>
            </label>
          </>
        )}
        <label className="text-gray-400">
          Segmento
          <select className={`${selectClass} mt-1`} value={state.segment} onChange={e => onChange({ segment: e.target.value })}>
            <option value="">Todos</option>
            {segments.map(s => <option key={s} value={s}>{s}</option>)}
          </select>
        </label>
      </div>

      {/* Um bloco por coluna, sem borda: rótulo e unidade em cima, mín e máx lado a lado com a largura toda do
          bloco — o PL em R$ milhões chega a 9 dígitos. */}
      <div className="grid gap-x-4 gap-y-2 [grid-template-columns:repeat(auto-fill,minmax(min(300px,100%),1fr))]">
        {NUMERIC_COLUMNS[tab].map(c => {
          const r = state.ranges[c.key] ?? { min: null, max: null };
          const meta = c.indicator ? INDICATOR_META[c.indicator] : null;
          return (
            <div key={c.key}>
              <div className="mb-1 flex items-center justify-between">
                <span className="flex items-center gap-1 text-gray-300">
                  {meta ? meta.label : c.key === 'price' ? 'Preço' : 'Patrimônio líquido'}
                  {meta && <InfoTooltip label={`O que é ${meta.label}`} content={<><strong>{meta.name}.</strong> {meta.formula}</>} />}
                </span>
                <span className="text-[11px] text-gray-500">{unitHint(c)}</span>
              </div>
              <div className="flex items-center gap-1.5">
                <RangeInput column={c} label={`${c.label} mínimo`} placeholder="mín" value={r.min}
                  onChange={v => setRange(c.key, 'min', v)} />
                <span className="text-gray-500">a</span>
                <RangeInput column={c} label={`${c.label} máximo`} placeholder="máx" value={r.max}
                  onChange={v => setRange(c.key, 'max', v)} />
              </div>
            </div>
          );
        })}
      </div>
      <p className="text-[11px] text-gray-500">
        Percentuais em pontos (DY 6,00 = 6%); patrimônio líquido em R$ milhões. Campo vazio = sem limite. O botão ± aparece
        nos indicadores que podem ser negativos. Com uma faixa preenchida, quem não tem o indicador sai da lista.
      </p>
    </div>
  );
};
