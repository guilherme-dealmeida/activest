import { X } from 'lucide-react';
import { LocalAssetSearch } from '@/components/ui/LocalAssetSearch';
import { DividendType } from '@/types';
import { TYPE_GROUP_LABELS } from './dividendLabels';
import { ALL, DividendFilterState } from './useDividendFilters';

/** Mesmo aviso do Listar das Transações: aparece com um ativo filtrado (pelo Listar, pela busca ou pelo seletor). */
export const DividendFilterNotice = ({ f }: { f: DividendFilterState }) => {
  if (f.filterAsset === ALL) return null;
  return (
    <div className="flex items-center gap-3 rounded-md border border-accent/30 bg-accent/10 px-4 py-2 text-sm">
      <span className="text-gray-200">
        Mostrando apenas os proventos de <span className="font-medium text-white">{f.filterAsset}</span>
      </span>
      <button
        type="button"
        onClick={f.clear}
        className="ml-auto flex items-center gap-1 text-xs text-gray-300 hover:text-white transition-colors"
      >
        <X className="w-3.5 h-3.5" />
        Limpar filtro
      </button>
    </div>
  );
};

const selectClass =
  'bg-surface-light border border-white/10 text-white text-sm rounded-md focus:ring-accent focus:border-accent p-2 disabled:opacity-60 disabled:cursor-not-allowed';

/** Busca por ticker ou nome (escolher = Listar), Tipo de Ativo → Ativo e Tipo de provento. */
export const DividendFilterBar = ({ f }: { f: DividendFilterState }) => (
  <div className="flex flex-wrap gap-3 items-end">
    <div className="w-full sm:w-96">
      <label className="block text-xs text-gray-400 mb-1">Buscar</label>
      <LocalAssetSearch options={f.searchOptions} onSelect={f.listTicker} ariaLabel="Buscar provento por ticker ou nome" />
    </div>
    <div>
      <label className="block text-xs text-gray-400 mb-1">Tipo de Ativo</label>
      <select value={f.filterAssetType} onChange={(e) => f.changeAssetType(e.target.value)} className={selectClass}>
        <option value={ALL}>Todos</option>
        {f.assetTypeOptions.map(o => <option key={o.id} value={o.id}>{o.label}</option>)}
      </select>
    </div>
    <div>
      <label className="block text-xs text-gray-400 mb-1">Ativo</label>
      <select
        value={f.filterAsset}
        disabled={f.filterAssetType === ALL}
        onChange={(e) => f.changeAsset(e.target.value)}
        className={selectClass}
      >
        <option value={ALL}>Todos</option>
        {f.assetOptions.map(a => <option key={a} value={a}>{a}</option>)}
      </select>
    </div>
    <div>
      <label className="block text-xs text-gray-400 mb-1">Tipo</label>
      <select value={f.filterType} onChange={(e) => f.changeType(e.target.value)} className={selectClass}>
        <option value={ALL}>Todos</option>
        {f.typeOptions.map((t: DividendType) => <option key={t} value={t}>{TYPE_GROUP_LABELS[t]}</option>)}
      </select>
    </div>
  </div>
);
