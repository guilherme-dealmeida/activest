import React, { useState } from 'react';
import { ChevronDown, ChevronUp, ChevronsUpDown } from 'lucide-react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export interface Column<T> {
  header: string;
  accessor: keyof T | ((row: T) => any);
  render?: (value: any, row: T) => React.ReactNode;
  sortable?: boolean;
  align?: 'left' | 'center' | 'right';
}

interface DataTableProps<T> {
  data: T[];
  columns: Column<T>[];
  keyField: keyof T;
  className?: string;
}

export function DataTable<T>({ data, columns, keyField, className }: DataTableProps<T>) {
  const [sortConfig, setSortConfig] = useState<{ key: number; direction: 'asc' | 'desc' } | null>(null);

  const handleSort = (index: number) => {
    if (!columns[index].sortable) return;
    
    let direction: 'asc' | 'desc' = 'asc';
    if (sortConfig && sortConfig.key === index && sortConfig.direction === 'asc') {
      direction = 'desc';
    }
    setSortConfig({ key: index, direction });
  };

  const sortedData = React.useMemo(() => {
    if (!sortConfig) return data;

    const column = columns[sortConfig.key];
    
    return [...data].sort((a, b) => {
      const aValue = typeof column.accessor === 'function' ? column.accessor(a) : a[column.accessor];
      const bValue = typeof column.accessor === 'function' ? column.accessor(b) : b[column.accessor];

      if (aValue < bValue) return sortConfig.direction === 'asc' ? -1 : 1;
      if (aValue > bValue) return sortConfig.direction === 'asc' ? 1 : -1;
      return 0;
    });
  }, [data, sortConfig, columns]);

  return (
    <div className={twMerge(clsx("w-full overflow-x-auto rounded-lg border border-surface-light bg-surface", className))}>
      <table className="w-full text-sm text-left">
        <thead className="text-xs text-gray-400 uppercase bg-surface-light/50 border-b border-surface-light">
          <tr>
            {columns.map((col, idx) => (
              <th 
                key={idx} 
                className={clsx(
                  "px-4 py-3 font-medium tracking-wider",
                  col.align === 'right' && 'text-right',
                  col.align === 'center' && 'text-center',
                  col.sortable && 'cursor-pointer hover:bg-surface-light transition-colors'
                )}
                onClick={() => handleSort(idx)}
              >
                <div className={clsx("flex items-center", col.align === 'right' ? 'justify-end' : col.align === 'center' ? 'justify-center' : 'justify-start')}>
                  {col.header}
                  {col.sortable && (
                    <span className="ml-1 inline-flex flex-col">
                      {sortConfig?.key === idx ? (
                        sortConfig.direction === 'asc' ? <ChevronUp className="h-3 w-3" /> : <ChevronDown className="h-3 w-3" />
                      ) : (
                        <ChevronsUpDown className="h-3 w-3 text-gray-600" />
                      )}
                    </span>
                  )}
                </div>
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-surface-light">
          {sortedData.map((row, rowIndex) => (
            <tr key={String(row[keyField]) || rowIndex} className="hover:bg-surface-light/30 transition-colors">
              {columns.map((col, colIndex) => {
                const value = typeof col.accessor === 'function' ? col.accessor(row) : row[col.accessor];
                return (
                  <td 
                    key={colIndex} 
                    className={clsx(
                      "px-4 py-3 whitespace-nowrap",
                      col.align === 'right' && 'text-right',
                      col.align === 'center' && 'text-center'
                    )}
                  >
                    {col.render ? col.render(value, row) : (value as React.ReactNode)}
                  </td>
                );
              })}
            </tr>
          ))}
          {sortedData.length === 0 && (
            <tr>
              <td colSpan={columns.length} className="px-4 py-8 text-center text-gray-500">
                Nenhum dado encontrado.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
