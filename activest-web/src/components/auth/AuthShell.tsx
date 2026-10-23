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
  <div className="min-h-screen bg-background flex items-center justify-center p-4">
    <div className={wide ? 'w-full max-w-lg' : 'w-full max-w-md'}>
      <div className="text-center mb-8">
        <h1 className="text-3xl font-bold text-accent">Prime<span className="text-white">Vest</span></h1>
        <p className="text-gray-400 mt-2 text-sm italic">Disciplina leva à liberdade</p>
      </div>
      <div className="bg-surface rounded-xl p-8 border border-white/10">
        <h2 className="text-xl font-semibold text-white">{title}</h2>
        {subtitle && <p className="text-sm text-gray-400 mt-1">{subtitle}</p>}
        <div className="mt-6">{children}</div>
      </div>
      {footer && <div className="mt-6 text-center text-sm text-gray-400">{footer}</div>}
    </div>
  </div>
);

export const FormError = ({ message }: { message?: string }) =>
  message ? (
    <div className="mb-4 p-3 bg-red-500/10 border border-red-500/30 rounded-lg text-red-400 text-sm">{message}</div>
  ) : null;

export const FormSuccess = ({ message }: { message?: string }) =>
  message ? (
    <div className="mb-4 p-3 bg-green-500/10 border border-green-500/30 rounded-lg text-green-400 text-sm">{message}</div>
  ) : null;
