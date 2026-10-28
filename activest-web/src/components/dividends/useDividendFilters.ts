import { useMemo, useState } from 'react';
import { DividendResponse } from '@/types';
import { LocalAssetOption } from '@/components/ui/LocalAssetSearch';
import { TYPE_ORDER } from './dividendLabels';

export const ALL = 'ALL';

const sortPtBr = (a: string, b: string) => a.localeCompare(b, 'pt-BR');

/**
 * Filtros de uma aba de Dividendos (Evolução dos Proventos, Histórico e A Receber): busca por ticker ou nome, Tipo de
 * Ativo → Ativo e Tipo de provento, no navegador, sobre os proventos da aba. `onChange` roda a cada mudança (a lista
 * volta à página 1). Era o estado do `DividendsTabPanel` até 27/10/2026; saiu dele para as três abas filtrarem igual.
 */
export const useDividendFilters = <T extends DividendResponse>(rows: T[], onChange?: () => void) => {
  const [filterType, setFilterType] = useState<string>(ALL);
  const [filterAssetType, setFilterAssetType] = useState<string>(ALL);
  const [filterAsset, setFilterAsset] = useState<string>(ALL);

  // Opções de cada filtro: só o que existe nos proventos da aba.
  const typeOptions = useMemo(() => {
    const present = new Set(rows.map(r => r.type));
    return TYPE_ORDER.filter(t => present.has(t));
  }, [rows]);

  const assetTypeOptions = useMemo(() => {
    const byId = new Map<number, string>();
    rows.forEach(r => {
      if (r.assetTypeId != null && !byId.has(r.assetTypeId)) {
        byId.set(r.assetTypeId, r.assetTypeDescription ?? String(r.assetTypeId));
      }
    });
    return [...byId.entries()].sort((a, b) => a[0] - b[0]).map(([id, label]) => ({ id: String(id), label }));
  }, [rows]);

  // O filtro de ativo depende do tipo de ativo: sem tipo escolhido fica em "Todos".
  const assetOptions = useMemo(() => {
    if (filterAssetType === ALL) return [];
    const tickers = rows
      .filter(r => String(r.assetTypeId) === filterAssetType)
      .map(r => r.ticker)
      .filter(Boolean);
    return [...new Set(tickers)].sort(sortPtBr);
  }, [rows, filterAssetType]);

  const filtered = useMemo(
    () =>
      rows.filter(r => {
        if (filterType !== ALL && r.type !== filterType) return false;
        if (filterAssetType !== ALL && String(r.assetTypeId) !== filterAssetType) return false;
        if (filterAsset !== ALL && r.ticker !== filterAsset) return false;
        return true;
      }),
    [rows, filterType, filterAssetType, filterAsset]
  );

  const changeType = (value: string) => {
    setFilterType(value);
    onChange?.();
  };

  // Trocar o tipo de ativo zera o ativo: um ticker de outro tipo deixaria a lista vazia sem motivo.
  const changeAssetType = (value: string) => {
    setFilterAssetType(value);
    setFilterAsset(ALL);
    onChange?.();
  };

  const changeAsset = (value: string) => {
    setFilterAsset(value);
    onChange?.();
  };

  /**
   * Ícone Listar da linha: filtra pelo Tipo de Ativo e pelo Ativo do provento.
   * O filtro Tipo (de provento) volta a "Todos", como os demais filtros no
   * Listar das Transações: a lista mostra todos os proventos daquele ativo.
   */
  const list = (row: Pick<DividendResponse, 'assetTypeId' | 'ticker'>) => {
    if (row.assetTypeId == null) return;
    setFilterAssetType(String(row.assetTypeId));
    setFilterAsset(row.ticker);
    setFilterType(ALL);
    onChange?.();
  };

  // Ativos da aba para a busca por ticker ou nome: um por ticker.
  const searchOptions = useMemo<LocalAssetOption[]>(() => {
    const byTicker = new Map<string, LocalAssetOption>();
    rows.forEach(r => {
      if (r.ticker && r.assetTypeId != null && !byTicker.has(r.ticker)) {
        byTicker.set(r.ticker, { ticker: r.ticker, name: r.assetName ?? '', typeLabel: r.assetTypeDescription ?? '' });
      }
    });
    return [...byTicker.values()];
  }, [rows]);

  /** Escolha na busca: o mesmo efeito do Listar, a partir de um provento daquele ativo. */
  const listTicker = (ticker: string) => {
    const row = rows.find(r => r.ticker === ticker);
    if (row) list(row);
  };

  /** "Limpar filtro" do aviso: volta a aba inteira (os três filtros em "Todos"). */
  const clear = () => {
    setFilterAssetType(ALL);
    setFilterAsset(ALL);
    setFilterType(ALL);
    onChange?.();
  };

  return {
    filterType, filterAssetType, filterAsset, typeOptions, assetTypeOptions, assetOptions, searchOptions, filtered,
    changeType, changeAssetType, changeAsset, list, listTicker, clear,
  };
};

export type DividendFilterState = ReturnType<typeof useDividendFilters>;
