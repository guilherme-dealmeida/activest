import { useState, useMemo } from 'react';

type SortDirection = 'asc' | 'desc' | null;

export interface SortConfig {
  key: string;
  direction: SortDirection;
}

export function useSortable<T>(data: T[], initialSort: SortConfig = { key: '', direction: null }) {
  const [sortConfig, setSortConfig] = useState<SortConfig>(initialSort);

  const requestSort = (key: string) => {
    let direction: SortDirection = 'asc';
    if (sortConfig.key === key) {
      if (sortConfig.direction === 'asc') direction = 'desc';
      else if (sortConfig.direction === 'desc') direction = null;
    }
    setSortConfig({ key, direction });
  };

  const sortedData = useMemo(() => {
    if (!sortConfig.direction || !sortConfig.key) return data;

    return [...data].sort((a, b) => {
      // A chave vem do cabeçalho clicado (string), então o acesso é dinâmico.
      const aVal = (a as Record<string, unknown>)[sortConfig.key];
      const bVal = (b as Record<string, unknown>)[sortConfig.key];

      if (aVal == null && bVal == null) return 0;
      if (aVal == null) return 1;
      if (bVal == null) return -1;

      let comparison = 0;
      if (typeof aVal === 'number' && typeof bVal === 'number') {
        comparison = aVal - bVal;
      } else {
        comparison = String(aVal).localeCompare(String(bVal), 'pt-BR', { numeric: true });
      }

      return sortConfig.direction === 'asc' ? comparison : -comparison;
    });
  }, [data, sortConfig]);

  const getSortIndicator = (key: string) => {
    if (sortConfig.key !== key || !sortConfig.direction) return ' ↕';
    return sortConfig.direction === 'asc' ? ' ↑' : ' ↓';
  };

  const getSortClass = (key: string) => {
    if (sortConfig.key !== key || !sortConfig.direction) return 'text-gray-400';
    return 'text-accent';
  };

  return { sortedData, requestSort, getSortIndicator, getSortClass, sortConfig };
}
