import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { Building2, Clock, SlidersHorizontal, X } from 'lucide-react';
import { clsx } from 'clsx';
import { indicatorsApi } from '@/api/indicators';
import { AssetLogo } from '@/components/ui/AssetLogo';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { Pagination } from '@/components/ui/Pagination';
import { TABLE_ACTION_CLASS } from '@/components/ui/tableAction';
import { TickerLink } from '@/components/issuer/TickerLink';
import { IssuerListingFilters } from '@/components/issuer/IssuerListingFilters';
import { formatDate } from '@/utils/format';
import { MONTHS, formatIndicator } from '@/utils/indicators';
import { apiMessage } from '@/utils/apiError';
import {
  ListingRow, ListingTab, NUMERIC_COLUMNS, NumericColumn, TAB_KIND, TabState, activeFilterCount, applyFilters,
  nextSort, readTabState, sortRows, toListingRows, writeTabState,
} from '@/utils/issuerListing';

/** "Informe de ago/2026" — o fim do mês vem do backend. */
const monthLabel = (iso: string) => {
  const [y, m] = iso.split('-').map(Number);
  return `${MONTHS[m - 1].slice(0, 3)}/${y}`;
};

const nf2 = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** PL abreviado (A1), sempre com duas casas: R$ 480,95 bi, R$ 972,63 mi. Abaixo de um milhão, o valor cheio. */
const formatEquity = (value: number | null): string => {
  if (value === null) return '—';
  const abs = Math.abs(value);
  const sign = value < 0 ? '-' : '';
  if (abs >= 1e9) return `${sign}R$ ${nf2.format(abs / 1e9)} bi`;
  if (abs >= 1e6) return `${sign}R$ ${nf2.format(abs / 1e6)} mi`;
  return `${sign}R$ ${nf2.format(abs)}`;
};

/** Relógio de "dado antigo" (A2), com a data no hover. */
const StaleMark = ({ title }: { title: string }) => (
  <span title={title} aria-label={title} className="inline-flex cursor-help text-accent">
    <Clock className="h-3.5 w-3.5" />
  </span>
);

const NumericCell = ({ row, column, tab }: { row: ListingRow; column: NumericColumn; tab: ListingTab }) => {
  const value = row.values[column.key];
  const note = row.notes[column.key];
  let text: string;
  let title: string | undefined;
  if (column.unit === 'EQUITY') {
    text = formatEquity(value);
    title = value === null ? 'Sem balanço ou informe' : value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  } else {
    text = formatIndicator(value, column.unit);
    title = value === null ? (note ?? (column.key === 'price' ? 'Sem cotação' : undefined)) : undefined;
  }
  let stale: string | null = null;
  if (column.key === 'price' && row.priceStale && row.priceDate) {
    stale = `Último pregão em ${formatDate(row.priceDate)}`;
  }
  if (column.unit === 'EQUITY' && row.baseStale && row.basePeriodEnd) {
    stale = tab === 'stocks' ? `Balanço de ${formatDate(row.basePeriodEnd)}` : `Informe de ${monthLabel(row.basePeriodEnd)}`;
  }
  return (
    <td className={clsx('px-3 py-3 text-right whitespace-nowrap',
      value === null ? 'text-gray-500' : column.unit === 'EQUITY' && value < 0 ? 'text-danger' : 'text-gray-200')}>
      <span className="inline-flex items-center justify-end gap-1">
        {stale && <StaleMark title={stale} />}
        <span title={title} className={title && value === null ? 'cursor-help' : undefined}>{text}</span>
      </span>
    </td>
  );
};

/**
 * Uma aba da listagem de emissoras (docs/planos/PLANO_LISTAGEM_EMISSORAS.md, 5): a aba inteira vem do backend
 * numa chamada; nome, filtros, ordem e página são aplicados aqui e ficam na URL.
 */
export const IssuerListingTable = ({ tab }: { tab: ListingTab }) => {
  const [params, setParams] = useSearchParams();
  const state = useMemo(() => readTabState(params, tab), [params, tab]);
  const [showFilters, setShowFilters] = useState(() => activeFilterCount(state) > 0);

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['issuer-listing', TAB_KIND[tab]],
    queryFn: () => indicatorsApi.listing(TAB_KIND[tab]),
    staleTime: 10 * 60_000,
  });

  const rows = useMemo(() => toListingRows(data ?? [], tab), [data, tab]);
  const filtered = useMemo(() => applyFilters(rows, tab, state), [rows, tab, state]);
  const sorted = useMemo(() => sortRows(filtered, state.sort), [filtered, state.sort]);

  const totalPages = Math.max(1, Math.ceil(sorted.length / state.pageSize));
  const page = Math.min(state.page, totalPages);
  const visible = sorted.slice((page - 1) * state.pageSize, page * state.pageSize);

  const update = (patch: Partial<TabState>, keepPage = false) => {
    const next = { ...state, ...patch };
    if (!keepPage) next.page = 1;
    setParams(writeTabState(params, tab, next), { replace: true });
  };
  const clearFilters = () => update({ name: '', sector: '', subsector: '', segment: '', ranges: {} });

  const columns = NUMERIC_COLUMNS[tab];
  const filters = activeFilterCount(state);

  const header = (key: string, label: string, align: 'left' | 'right' = 'right') => {
    const active = state.sort ? state.sort.key === key : key === 'equity';
    const dir = state.sort ? state.sort.dir : 'desc';
    return (
      <th key={key}
        className={clsx('px-3 py-3 cursor-pointer select-none whitespace-nowrap', align === 'right' && 'text-right',
          active ? 'text-accent' : 'text-gray-400')}
        onClick={() => update({ sort: nextSort(state.sort, key) }, true)}
        aria-sort={active ? (dir === 'asc' ? 'ascending' : 'descending') : 'none'}>
        {label}{active ? (dir === 'asc' ? ' ↑' : ' ↓') : ' ↕'}
      </th>
    );
  };

  if (isLoading) {
    return <div className="flex h-48 items-center justify-center"><LoadingSpinner className="h-8 w-8 text-accent" /></div>;
  }
  if (isError) {
    return <p className="text-sm text-gray-400">{apiMessage(error) ?? 'Não foi possível carregar a listagem.'}</p>;
  }

  return (
    <div className="rounded-xl border border-surface-light bg-surface p-5 space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <input
          type="search"
          value={state.name}
          onChange={e => update({ name: e.target.value })}
          placeholder="Filtrar por ticker ou nome"
          aria-label="Filtrar a tabela por ticker ou nome"
          className="w-full max-w-xs rounded-md border border-white/10 bg-surface-light px-3 py-2 text-sm text-white placeholder-gray-500 focus:border-accent focus:ring-accent"
        />
        <button
          type="button"
          onClick={() => setShowFilters(v => !v)}
          aria-expanded={showFilters}
          className={clsx('flex items-center gap-2 rounded-md border px-3 py-2 text-sm transition-colors',
            showFilters || filters > 0 ? 'border-accent/50 text-accent' : 'border-white/10 text-gray-300 hover:text-white')}
        >
          <SlidersHorizontal className="h-4 w-4" />
          Filtros{filters > 0 && <span className="rounded-full bg-accent px-1.5 text-xs font-semibold text-black">{filters}</span>}
        </button>
        {(filters > 0 || state.name) && (
          <button type="button" onClick={clearFilters}
            className="flex items-center gap-1 text-xs text-gray-300 hover:text-white transition-colors">
            <X className="h-3.5 w-3.5" />
            Limpar filtros
          </button>
        )}
        <span className="ml-auto text-sm text-gray-400">
          {sorted.length === rows.length ? `${rows.length} ativos` : `${sorted.length} de ${rows.length} ativos`}
        </span>
      </div>

      {showFilters && <IssuerListingFilters tab={tab} rows={rows} state={state} onChange={patch => update(patch)} />}

      <div className="overflow-x-auto">
        <table className="w-full text-sm text-left">
          <thead className="text-xs uppercase border-b border-white/10">
            <tr>
              {header('ticker', 'ATIVO', 'left')}
              {header('name', 'NOME', 'left')}
              {header('sector', tab === 'stocks' ? 'SETOR' : 'SEGMENTO', 'left')}
              {columns.map(c => header(c.key, c.label))}
              <th className="px-3 py-3 text-center text-gray-400">AÇÃO</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-white/5">
            {visible.map(row => (
              <tr key={row.ticker} className="hover:bg-white/5 transition-colors">
                <td className="px-3 py-3 text-white">
                  <div className="flex items-center gap-3">
                    <AssetLogo ticker={row.ticker} hasLogo={row.hasLogo} size={38} shape="square" />
                    <TickerLink ticker={row.ticker} issuerId={row.issuerId} />
                  </div>
                </td>
                <td className="px-3 py-3 text-gray-200">
                  <span className="block max-w-[16rem] truncate" title={row.legalName ?? row.name}>{row.name}</span>
                </td>
                <td className="px-3 py-3">
                  {tab === 'stocks' ? (
                    <div className="max-w-[16rem]" title={[row.sector, row.subsector, row.segment].filter(Boolean).join(' › ')}>
                      <span className="block truncate text-gray-200">{row.sector ?? '—'}</span>
                      {(row.subsector || row.segment) && (
                        <span className="block truncate text-xs text-gray-500">
                          {[row.subsector, row.segment].filter(Boolean).join(' › ')}
                        </span>
                      )}
                    </div>
                  ) : (
                    <span className="block max-w-[12rem] truncate text-gray-200" title={row.segment ?? undefined}>
                      {row.segment ?? '—'}
                    </span>
                  )}
                </td>
                {columns.map(c => <NumericCell key={c.key} row={row} column={c} tab={tab} />)}
                <td className="px-3 py-3 text-center">
                  <Link to={`/assets/${encodeURIComponent(row.ticker)}`} className={`inline-flex ${TABLE_ACTION_CLASS}`}
                    title="Abrir ativo" aria-label={`Abrir ativo ${row.ticker}`}>
                    <Building2 className="h-4 w-4" />
                  </Link>
                </td>
              </tr>
            ))}
            {visible.length === 0 && (
              <tr>
                <td colSpan={columns.length + 4} className="px-3 py-10 text-center text-gray-400">
                  Nenhum ativo com esses filtros.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <Pagination
        currentPage={page}
        totalPages={totalPages}
        onPageChange={p => update({ page: p }, true)}
        totalItems={sorted.length}
        itemsPerPage={state.pageSize}
        onItemsPerPageChange={size => update({ pageSize: size })}
      />
    </div>
  );
};
