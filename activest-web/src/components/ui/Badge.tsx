import React from 'react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  variant?: 'success' | 'danger' | 'warning' | 'info' | 'default';
}

export const Badge = ({ children, variant = 'default', className, ...props }: BadgeProps) => {
  const variants = {
    success: 'bg-success/20 text-success border-success/30',
    danger: 'bg-danger/20 text-danger border-danger/30',
    warning: 'bg-yellow-500/20 text-yellow-500 border-yellow-500/30',
    info: 'bg-blue-500/20 text-blue-500 border-blue-500/30',
    default: 'bg-surface-light text-gray-300 border-gray-700',
  };

  return (
    <span 
      className={twMerge(clsx("inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border", variants[variant], className))} 
      {...props}
    >
      {children}
    </span>
  );
};
