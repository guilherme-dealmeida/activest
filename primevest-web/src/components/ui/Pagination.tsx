import React from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { clsx } from 'clsx';

interface PaginationProps {
  currentPage: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  totalItems?: number;
  itemsPerPage: number;
  onItemsPerPageChange: (size: number) => void;
}

const PAGE_SIZE_OPTIONS = [10, 25, 50, 100];

export const Pagination = ({ currentPage, totalPages, onPageChange, totalItems, itemsPerPage, onItemsPerPageChange }: PaginationProps) => {
  const pages: (number | string)[] = [];
  
  if (totalPages <= 7) {
    for (let i = 1; i <= totalPages; i++) pages.push(i);
  } else {
    pages.push(1);
    if (currentPage > 3) pages.push('...');
    for (let i = Math.max(2, currentPage - 1); i <= Math.min(totalPages - 1, currentPage + 1); i++) {
      pages.push(i);
    }
    if (currentPage < totalPages - 2) pages.push('...');
    pages.push(totalPages);
  }

  const startItem = totalItems ? (currentPage - 1) * itemsPerPage + 1 : undefined;
  const endItem = totalItems ? Math.min(currentPage * itemsPerPage, totalItems) : undefined;

  return (
    <div className="flex items-center justify-between mt-4 pt-4 border-t border-white/10 flex-wrap gap-3">
      <div className="flex items-center gap-3">
        <div className="flex items-center gap-2">
          <span className="text-sm text-gray-400">Exibir</span>
          <select
            value={itemsPerPage}
            onChange={(e) => {
              onItemsPerPageChange(Number(e.target.value));
              onPageChange(1);
            }}
            className="bg-transparent border border-white/10 text-gray-300 text-sm rounded-md px-2 py-1 focus:ring-accent focus:border-accent"
          >
            {PAGE_SIZE_OPTIONS.map(size => (
              <option key={size} value={size} className="bg-[#111] text-white">{size}</option>
            ))}
          </select>
          <span className="text-sm text-gray-400">por página</span>
        </div>
        {totalItems !== undefined && startItem && endItem && (
          <span className="text-sm text-gray-500">
            | {startItem}-{endItem} de {totalItems}
          </span>
        )}
      </div>

      {totalPages > 1 && (
        <div className="flex items-center gap-1">
          <button
            onClick={() => onPageChange(currentPage - 1)}
            disabled={currentPage === 1}
            className={clsx(
              "p-1.5 rounded-md transition-colors",
              currentPage === 1
                ? "text-gray-600 cursor-not-allowed"
                : "text-gray-400 hover:text-white hover:bg-white/10"
            )}
          >
            <ChevronLeft className="h-4 w-4" />
          </button>
          
          {pages.map((page, idx) => (
            typeof page === 'string' ? (
              <span key={`dot-${idx}`} className="px-2 text-gray-500">...</span>
            ) : (
              <button
                key={page}
                onClick={() => onPageChange(page)}
                className={clsx(
                  "min-w-[32px] h-8 px-2 rounded-md text-sm font-medium transition-colors",
                  page === currentPage
                    ? "bg-accent text-black"
                    : "text-gray-400 hover:text-white hover:bg-white/10"
                )}
              >
                {page}
              </button>
            )
          ))}

          <button
            onClick={() => onPageChange(currentPage + 1)}
            disabled={currentPage === totalPages}
            className={clsx(
              "p-1.5 rounded-md transition-colors",
              currentPage === totalPages
                ? "text-gray-600 cursor-not-allowed"
                : "text-gray-400 hover:text-white hover:bg-white/10"
            )}
          >
            <ChevronRight className="h-4 w-4" />
          </button>
        </div>
      )}
    </div>
  );
};
