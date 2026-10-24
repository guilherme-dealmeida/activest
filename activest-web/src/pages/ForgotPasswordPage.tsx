import React, { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { authApi } from '@/api/auth';
import { AuthShell, FormError } from '@/components/auth/AuthShell';
import { CodeStep } from '@/components/auth/CodeStep';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { CodeSent } from '@/types';
import { formErrorMessage } from '@/utils/apiError';

/**
 * "Esqueci a senha" (docs/planos/PLANO_CADASTRO_USUARIO.md, 3.5): e-mail → código + nova senha.
 * Serve também a quem só entrava pelo Google e quer uma senha. A resposta é a mesma para e-mail com
 * ou sem conta (D10).
 */
export const ForgotPasswordPage = () => {
  const navigate = useNavigate();
  const initial = (useLocation().state as { email?: string } | null)?.email ?? '';
  const [email, setEmail] = useState(initial);
  const [sent, setSent] = useState<CodeSent | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [password, setPassword] = useState('');
  const [confirmValue, setConfirmValue] = useState('');

  const request = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      setSent(await authApi.forgot(email.trim()));
    } catch (err) {
      setError(formErrorMessage(err, 'Não foi possível enviar o código.'));
    } finally {
      setBusy(false);
    }
  };

  const passwordOk = password.length >= 8 && password.length <= 72;

  if (sent) {
    return (
      <AuthShell title="Redefinir a senha"
        footer={<Link to="/login" className="text-accent hover:underline">Voltar para o login</Link>}>
        <CodeStep
          sent={sent}
          confirmLabel="Salvar nova senha"
          extraValid={passwordOk && password === confirmValue}
          onConfirm={async (code) => {
            await authApi.reset(sent.requestId, code, password);
            navigate('/login', { state: { message: 'Senha redefinida. Entre com a nova senha.', email: email.trim() } });
          }}
          onResend={() => authApi.forgotResend(sent.requestId)}
          onBack={() => setSent(null)}
          backLabel="Trocar o e-mail"
        >
          <Input label="Nova senha" type="password" value={password} onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password" error={password && !passwordOk ? 'De 8 a 72 caracteres.' : undefined} />
          <Input label="Confirme a nova senha" type="password" value={confirmValue} onChange={(e) => setConfirmValue(e.target.value)}
            autoComplete="new-password" error={confirmValue && password !== confirmValue ? 'As senhas não conferem.' : undefined} />
        </CodeStep>
      </AuthShell>
    );
  }

  return (
    <AuthShell title="Esqueci a senha" subtitle="Informe o e-mail da conta: enviaremos um código para criar uma nova senha."
      footer={<Link to="/login" className="text-accent hover:underline">Voltar para o login</Link>}>
      <FormError message={error} />
      <form onSubmit={request} className="space-y-4">
        <Input label="E-mail" type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required />
        <Button type="submit" className="w-full" isLoading={busy} disabled={!email.trim()}>Enviar código</Button>
      </form>
    </AuthShell>
  );
};
