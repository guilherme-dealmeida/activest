import React from 'react';

interface Props {
  title: string;
  subtitle?: React.ReactNode;
  children: React.ReactNode;
  /** Abaixo do cartão (links "Já tem conta? Entrar"...). */
  footer?: React.ReactNode;
  wide?: boolean;
}

/** Moldura das telas públicas (entrar, criar conta, esqueci a senha): logo, cartão e rodapé. */
export const AuthShell = ({ title, subtitle, children, footer, wide }: Props) => (
  <div className="min-h-screen bg-bg flex items-center justify-center p-4">
    <div className={wide ? 'w-full max-w-lg' : 'w-full max-w-md'}>
      <div className="text-center mb-8">
        <h1 className="text-3xl font-bold text-accent-text">Acti<span className="text-content">vest</span></h1>
        <p className="text-content-3 mt-2 text-sm italic">Disciplina leva à liberdade</p>
      </div>
      <div className="bg-surface rounded-panel p-8 border border-line">
        <h2 className="text-xl font-semibold text-content">{title}</h2>
        {subtitle && <p className="text-sm text-content-3 mt-1">{subtitle}</p>}
        <div className="mt-6">{children}</div>
      </div>
      {footer && <div className="mt-6 text-center text-sm text-content-3">{footer}</div>}
    </div>
  </div>
);

export const FormError = ({ message }: { message?: string }) =>
  message ? (
    <div className="mb-4 p-3 bg-danger/10 border border-danger/30 rounded-control text-danger text-sm">{message}</div>
  ) : null;

export const FormSuccess = ({ message }: { message?: string }) =>
  message ? (
    <div className="mb-4 p-3 bg-success/10 border border-success/30 rounded-control text-success text-sm">{message}</div>
  ) : null;
