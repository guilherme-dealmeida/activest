import { ReactNode } from 'react';
import { InfoTooltip } from '@/components/ui/InfoTooltip';

/** Rótulo de campo do formulário de metas: obrigatório com asterisco vermelho, explicação no (i). */
export const FieldLabel = ({ children, required, info, htmlFor }: {
  children: ReactNode; required?: boolean; info?: ReactNode; htmlFor?: string;
}) => (
  <label htmlFor={htmlFor} className="mb-1.5 flex items-center gap-1 text-sm font-medium text-gray-300">
    {children}
    {required && <span className="text-danger" aria-label="obrigatório">*</span>}
    {info && <InfoTooltip content={info} />}
  </label>
);
