import React, { useCallback, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { authApi } from '@/api/auth';
import { useAuth } from '@/context/AuthContext';
import { useGoogleSignIn } from '@/hooks/useGoogleSignIn';
import { AuthShell, FormError } from '@/components/auth/AuthShell';
import { CodeStep } from '@/components/auth/CodeStep';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { CodeSent, SignupRequest } from '@/types';
import { formErrorMessage } from '@/utils/apiError';
import { formatPhone, isPhoneComplete } from '@/utils/phone';
import { formatCpf, isValidCpf } from '@/utils/integrations';

/**
 * Criar conta (docs/planos/PLANO_CADASTRO_USUARIO.md, 3.1): dados → código por e-mail → já entra.
 * Ou "Continuar com o Google", que dispensa o código (o aceite dos termos é a frase sob o botão, D17).
 */
export const SignupPage = () => {
  const navigate = useNavigate();
  const { startSession, loginWithGoogle } = useAuth();
  const { data: status, isLoading: loading_ } = useQuery({
    queryKey: ['signup-status'], queryFn: authApi.signupStatus, retry: false,
  });

  const [form, setForm] = useState({ name: '', email: '', phone: '', cpf: '', password: '', confirm: '' });
  const [accepted, setAccepted] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState<CodeSent | null>(null);

  const set = (field: keyof typeof form, amount: string) => setForm((f) => ({ ...f, [field]: amount }));

  const handleGoogle = useCallback(async (credential: string) => {
    await loginWithGoogle(credential);
    navigate('/');
  }, [loginWithGoogle, navigate]);
  const google = useGoogleSignIn({ onCredential: handleGoogle });

  const cpfDigits = form.cpf.replace(/\D/g, '');
  const cpfOk = cpfDigits.length === 0 || isValidCpf(form.cpf);
  const passwordOk = form.password.length >= 8 && form.password.length <= 72;
  const confirmOk = form.password === form.confirm;
  const canSend = form.name.trim() && form.email.trim() && passwordOk && confirmOk && cpfOk
    && isPhoneComplete(form.phone) && accepted;

  const send = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setBusy(true);
    const body: SignupRequest = {
      name: form.name.trim(),
      email: form.email.trim(),
      password: form.password,
      acceptTerms: accepted,
      phone: form.phone.trim() || undefined,
      cpf: cpfDigits || undefined,
    };
    try {
      setSent(await authApi.signupStart(body));
    } catch (err) {
      setError(formErrorMessage(err, 'Não foi possível iniciar o cadastro.'));
    } finally {
      setBusy(false);
    }
  };

  if (loading_) {
    return <AuthShell title="Criar conta"><p className="text-sm text-gray-400">Carregando...</p></AuthShell>;
  }

  if (!status?.enabled) {
    return (
      <AuthShell title="Criar conta"
        footer={<Link to="/login" className="text-accent hover:underline">Voltar para o login</Link>}>
        <p className="text-sm text-gray-300">
          O cadastro de novas contas está desativado no momento. Peça acesso a quem administra o PrimeVest.
        </p>
      </AuthShell>
    );
  }

  if (sent) {
    return (
      <AuthShell title="Confirme o seu e-mail"
        footer={<>Já tem conta? <Link to="/login" className="text-accent hover:underline">Entrar</Link></>}>
        <CodeStep
          sent={sent}
          confirmLabel="Criar conta"
          onConfirm={async (code) => {
            await startSession(await authApi.signupConfirm(sent.requestId, code));
            navigate('/dashboard');
          }}
          onResend={() => authApi.signupResend(sent.requestId)}
          onBack={() => setSent(null)}
        />
      </AuthShell>
    );
  }

  return (
    <AuthShell title="Criar conta" subtitle="Leva um minuto: os dados e um código enviado ao seu e-mail." wide
      footer={<>Já tem conta? <Link to="/login" className="text-accent hover:underline">Entrar</Link></>}>
      <FormError message={error || google.error} />
      <form onSubmit={send} className="space-y-4" autoComplete="on">
        <Input label="Nome" value={form.name} onChange={(e) => set('name', e.target.value)} maxLength={255}
          autoComplete="name" required />
        <Input label="E-mail" type="email" value={form.email} onChange={(e) => set('email', e.target.value)}
          maxLength={255} autoComplete="email" hint="É o seu login. Enviaremos um código para confirmá-lo." required />
        <div className="grid gap-4 sm:grid-cols-2">
          <Input label="Celular (opcional)" value={form.phone} inputMode="tel" autoComplete="tel"
            onChange={(e) => set('phone', formatPhone(e.target.value))} placeholder="(11) 99999-9999"
            error={!isPhoneComplete(form.phone) ? 'Informe DDD e número.' : undefined} />
          <Input label="CPF (opcional)" value={form.cpf} inputMode="numeric" maxLength={14}
            onChange={(e) => set('cpf', formatCpf(e.target.value))} placeholder="000.000.000-00"
            error={cpfDigits.length === 11 && !cpfOk ? 'CPF inválido.' : undefined}
            info="Usado só nas integrações que dependem dele, como a B3. Guardado cifrado; na tela aparece mascarado." />
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <Input label="Senha" type="password" value={form.password} autoComplete="new-password"
            onChange={(e) => set('password', e.target.value)}
            error={form.password && !passwordOk ? 'De 8 a 72 caracteres.' : undefined}
            hint="Mínimo de 8 caracteres." required />
          <Input label="Confirme a senha" type="password" value={form.confirm} autoComplete="new-password"
            onChange={(e) => set('confirm', e.target.value)}
            error={form.confirm && !confirmOk ? 'As senhas não conferem.' : undefined} required />
        </div>
        <label className="flex items-start gap-3 text-sm text-gray-300 cursor-pointer">
          <input type="checkbox" className="mt-1 accent-accent" checked={accepted} onChange={(e) => setAccepted(e.target.checked)} />
          <span>
            Li e aceito os <Link to="/terms" target="_blank" className="text-accent hover:underline">Termos de Uso</Link> e
            a <Link to="/privacy" target="_blank" className="text-accent hover:underline">Política de Privacidade</Link>.
          </span>
        </label>
        <Button type="submit" className="w-full" disabled={!canSend} isLoading={busy}>Continuar</Button>
      </form>

      {google.enabled && (
        <>
          <div className="flex items-center gap-3 my-6">
            <span className="h-px flex-1 bg-white/10" />
            <span className="text-xs uppercase tracking-wider text-gray-500">ou</span>
            <span className="h-px flex-1 bg-white/10" />
          </div>
          <div className="flex justify-center min-h-[44px]">
            {google.isLoading
              ? <span className="text-sm text-gray-400 self-center">Entrando com o Google...</span>
              : <div ref={google.containerRef} />}
          </div>
          <p className="mt-3 text-center text-xs text-gray-500">
            Ao continuar com o Google você aceita os{' '}
            <Link to="/terms" target="_blank" className="text-accent hover:underline">Termos de Uso</Link> e a{' '}
            <Link to="/privacy" target="_blank" className="text-accent hover:underline">Política de Privacidade</Link>.
          </p>
        </>
      )}
    </AuthShell>
  );
};
