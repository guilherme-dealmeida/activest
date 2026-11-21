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
    warning: 'bg-accent/20 text-accent-text border-accent/30',
    info: 'bg-info/20 text-info border-info/30',
    default: 'bg-elevated text-content-2 border-line-strong',
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
