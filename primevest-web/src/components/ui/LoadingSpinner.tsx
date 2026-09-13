import React from 'react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

interface LoadingSpinnerProps extends React.HTMLAttributes<HTMLDivElement> {}

export const LoadingSpinner = ({ className, ...props }: LoadingSpinnerProps) => {
  return (
    <div role="status" className={twMerge(clsx("inline-block animate-spin rounded-full border-4 border-solid border-current border-r-transparent align-[-0.125em] motion-reduce:animate-[spin_1.5s_linear_infinite]", className))} {...props}>
      <span className="!absolute !-m-px !h-px !w-px !overflow-hidden !whitespace-nowrap !border-0 !p-0 ![clip:rect(0,0,0,0)]">
        Loading...
      </span>
    </div>
  );
};
