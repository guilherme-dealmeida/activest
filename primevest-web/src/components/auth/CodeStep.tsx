import React, { useEffect, useState } from 'react';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { CodeSent } from '@/types';
import { formErrorMessage } from '@/utils/apiError';

interface Props {
  sent: CodeSent;
  /** Recusar aqui vira a mensagem de erro do passo. */
  onConfirm: (code: string) => Promise<void>;
  onResend: () => Promise<CodeSent>;
  onBack?: () => void;
  backLabel?: string;
  confirmLabel?: string;
  /** Campos extras antes do botão (ex.: nova senha). */
  children?: React.ReactNode;
  /** Os campos extras estão prontos? */
  extraValid?: boolean;
  danger?: boolean;
}

/**
 * Passo do código enviado por e-mail (docs/planos/PLANO_CADASTRO_USUARIO.md, 3.2): 6 dígitos,
 * reenviar com contagem regressiva, voltar. Usado no cadastro, no "esqueci a senha", na troca de
 * e-mail e na exclusão da conta.
 */
export const CodeStep = ({
  sent, onConfirm, onResend, onBack, backLabel = 'Corrigir dados', confirmLabel = 'Confirmar',
  children, extraValid = true, danger,
}: Props) => {
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [info, setInfo] = useState('');
  const [busy, setBusy] = useState(false);
  const [espera, setEspera] = useState(sent.resendInSeconds);
  const [destino, setDestino] = useState(sent.maskedEmail);

  useEffect(() => {
    if (espera <= 0) return;
    const t = setTimeout(() => setEspera((s) => s - 1), 1000);
    return () => clearTimeout(t);
  }, [espera]);

  const confirmar = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setInfo('');
    setBusy(true);
    try {
      await onConfirm(code);
    } catch (err) {
      setError(formErrorMessage(err, 'Não foi possível confirmar o código.'));
    } finally {
      setBusy(false);
    }
  };

  const reenviar = async () => {
    setError('');
    setInfo('');
    try {
      const novo = await onResend();
      setDestino(novo.maskedEmail);
      setEspera(novo.resendInSeconds);
      setCode('');
      setInfo('Enviamos um novo código. O anterior deixou de valer.');
    } catch (err) {
      setError(formErrorMessage(err, 'Não foi possível reenviar o código.'));
    }
  };

  const completo = code.replace(/\D/g, '').length === 6;

  return (
    <form onSubmit={confirmar} className="space-y-4">
      <p className="text-sm text-gray-300">
        Enviamos um código de 6 dígitos para <span className="text-white font-medium">{destino}</span>.
        Ele vale {Math.round(sent.expiresInSeconds / 60)} minutos.
      </p>
      {error && <div className="p-3 bg-red-500/10 border border-red-500/30 rounded-lg text-red-400 text-sm">{error}</div>}
      {info && <div className="p-3 bg-green-500/10 border border-green-500/30 rounded-lg text-green-400 text-sm">{info}</div>}
      <Input
        label="Código"
        value={code}
        onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
        inputMode="numeric"
        autoComplete="one-time-code"
        placeholder="000000"
        className="text-center text-xl tracking-[0.5em] font-mono"
        autoFocus
      />
      {children}
      <Button type="submit" className="w-full" variant={danger ? 'danger' : 'primary'}
        disabled={!completo || !extraValid} isLoading={busy}>
        {confirmLabel}
      </Button>
      <div className="flex items-center justify-between text-sm">
        {onBack ? (
          <button type="button" onClick={onBack} className="text-gray-400 hover:text-white">{backLabel}</button>
        ) : <span />}
        <button type="button" onClick={reenviar} disabled={espera > 0}
          className="text-accent hover:underline disabled:text-gray-500 disabled:no-underline disabled:cursor-not-allowed">
          {espera > 0 ? `Reenviar em ${espera}s` : 'Reenviar código'}
        </button>
      </div>
      <p className="text-xs text-gray-500">Não chegou? Confira a caixa de spam.</p>
    </form>
  );
};
