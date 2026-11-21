import React, { useState, useCallback } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '@/context/AuthContext';
import { useGoogleSignIn } from '@/hooks/useGoogleSignIn';
import { loginErrorMessage } from '@/utils/loginError';
import { authApi } from '@/api/auth';
import { AuthShell, FormError, FormSuccess } from '@/components/auth/AuthShell';

const inputClass = 'w-full rounded-control border border-line bg-elevated px-3 py-2.5 text-sm text-content placeholder-content-3 focus:ring-2 focus:ring-accent focus:border-transparent';

export const LoginPage = () => {
  const location = useLocation();
  const notice = (location.state as { message?: string; email?: string } | null) ?? null;
  const [email, setEmail] = useState(notice?.email ?? '');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const { login, loginWithGoogle } = useAuth();
  const navigate = useNavigate();

  // "Criar conta" só aparece com o cadastro ligado no servidor (D16).
  const { data: signup } = useQuery({ queryKey: ['signup-status'], queryFn: authApi.signupStatus, retry: false });

  const handleGoogle = useCallback(async (credential: string) => {
    await loginWithGoogle(credential);
    navigate('/');
  }, [loginWithGoogle, navigate]);

  const google = useGoogleSignIn({ onCredential: handleGoogle });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setIsLoading(true);
    try {
      await login(email, password);
      navigate('/');
    } catch (err: unknown) {
      setError(loginErrorMessage(err, {
        unauthorized: 'Email ou senha inválidos.',
        fallback: 'Não foi possível entrar. Tente novamente.',
      }));
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <AuthShell
      title="Entrar"
      footer={signup?.enabled && (
        <>Não tem conta? <Link to="/signup" className="text-accent-text hover:underline">Criar conta</Link></>
      )}
    >
      <FormSuccess message={notice?.message} />
      <FormError message={error || google.error} />
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-content-2 mb-1.5">Email</label>
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} className={inputClass}
            placeholder="seu@email.com" autoComplete="email" required />
        </div>
        <div>
          <div className="flex items-center justify-between mb-1.5">
            <label className="block text-sm font-medium text-content-2">Senha</label>
            <Link to="/forgot-password" state={{ email }} className="text-xs text-accent-text hover:underline">Esqueci a senha</Link>
          </div>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} className={inputClass}
            placeholder="••••••••" autoComplete="current-password" required />
        </div>
        <button
          type="submit"
          disabled={isLoading || google.isLoading}
          className="w-full bg-accent hover:bg-accent/90 text-black font-semibold py-2.5 px-4 rounded-control transition-colors disabled:opacity-50"
        >
          {isLoading ? 'Entrando...' : 'Entrar'}
        </button>
      </form>

      {/* Sem VITE_GOOGLE_CLIENT_ID o bloco inteiro some: um botão que não
          teria como funcionar é pior do que botão nenhum. */}
      {google.enabled && (
        <>
          <div className="flex items-center gap-3 my-6">
            <span className="h-px flex-1 bg-selected" />
            <span className="text-xs uppercase tracking-wider text-content-4">ou</span>
            <span className="h-px flex-1 bg-selected" />
          </div>

          <div className="flex justify-center min-h-[44px]">
            {google.isLoading
              ? <span className="text-sm text-content-3 self-center">Entrando com o Google...</span>
              : <div ref={google.containerRef} />}
          </div>
          {signup?.enabled && (
            <p className="mt-3 text-center text-xs text-content-4">
              Sem conta ainda? Ao continuar com o Google você cria uma e aceita os{' '}
              <Link to="/terms" target="_blank" className="text-accent-text hover:underline">Termos de Uso</Link> e a{' '}
              <Link to="/privacy" target="_blank" className="text-accent-text hover:underline">Política de Privacidade</Link>.
            </p>
          )}
        </>
      )}
    </AuthShell>
  );
};
