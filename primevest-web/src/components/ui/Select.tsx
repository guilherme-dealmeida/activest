import React from 'react';
import { clsx } from 'clsx';
import { LabelWithInfo } from './InfoTooltip';

interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  hint?: string;
  /** Explicação no (i) ao lado do rótulo. */
  info?: React.ReactNode;
}

/**
 * Select com o mesmo visual do Input. Existe porque as telas vinham montando
 * <select> cru com as classes repetidas em cada lugar.
 */
export const Select = React.forwardRef<HTMLSelectElement, SelectProps>(
  ({ label, hint, info, className, children, ...props }, ref) => (
    <div className="w-full">
      {label && (
        <label className="block text-sm font-medium text-gray-300 mb-1.5">
          <LabelWithInfo info={info}>{label}</LabelWithInfo>
        </label>
      )}
      <select
        ref={ref}
        className={clsx(
          'flex h-10 w-full rounded-md border border-surface-light bg-surface px-3 py-2 text-sm text-white',
          'focus:ring-2 focus:ring-accent focus:border-transparent',
          'disabled:opacity-60 disabled:cursor-not-allowed',
          className
        )}
        {...props}
      >
        {children}
      </select>
      {hint && <p className="text-xs text-gray-500 mt-1">{hint}</p>}
    </div>
  )
);
Select.displayName = 'Select';
