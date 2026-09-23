import React from 'react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';
import { LabelWithInfo } from './InfoTooltip';

interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  title?: string;
  /** Explicação no (i) ao lado do título. */
  titleInfo?: React.ReactNode;
  value?: string | React.ReactNode;
  icon?: React.ReactNode;
  description?: string | React.ReactNode;
}

export const Card = ({ title, titleInfo, value, icon, description, className, children, ...props }: CardProps) => {
  return (
    <div className={twMerge(clsx("bg-surface rounded-xl border border-surface-light p-5", className))} {...props}>
      {(title || value || icon) && (
        <div className="flex items-start justify-between mb-4">
          <div>
            {title && <h3 className="text-sm font-medium text-gray-400"><LabelWithInfo info={titleInfo}>{title}</LabelWithInfo></h3>}
            {value && <div className="mt-1 text-2xl font-semibold text-white">{value}</div>}
            {description && <div className="mt-1 text-sm">{description}</div>}
          </div>
          {icon && <div className="text-gray-400 p-2 bg-surface-light rounded-lg">{icon}</div>}
        </div>
      )}
      {children}
    </div>
  );
};
