import React, { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { useGoogleSignIn } from '@/hooks/useGoogleSignIn';

export const LoginPage = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const { login, loginWithGoogle } = useAuth();
  const navigate = useNavigate();

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
    } catch (err: any) {
      setError(err.response?.data?.message || 'Email ou senha inválidos');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-background flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <h1 className="text-3xl font-bold text-accent">Prime<span className="text-white">Vest</span></h1>
          <p className="text-gray-400 mt-2 text-sm italic">Disciplina leva à liberdade</p>
        </div>
        <div className="bg-surface rounded-xl p-8 border border-white/10">
          <h2 className="text-xl font-semibold text-white mb-6">Entrar</h2>
          {(error || google.error) && (
            <div className="mb-4 p-3 bg-red-500/10 border border-red-500/30 rounded-lg text-red-400 text-sm">
              {error || google.error}
            </div>
          )}
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-300 mb-1.5">Email</label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full rounded-md border border-surface-light bg-surface-light px-3 py-2.5 text-sm text-white placeholder-gray-500 focus:ring-2 focus:ring-accent focus:border-transparent"
                placeholder="seu@email.com"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-300 mb-1.5">Senha</label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full rounded-md border border-surface-light bg-surface-light px-3 py-2.5 text-sm text-white placeholder-gray-500 focus:ring-2 focus:ring-accent focus:border-transparent"
                placeholder="••••••••"
                required
              />
            </div>
            <button
              type="submit"
              disabled={isLoading || google.isLoading}
              className="w-full bg-accent hover:bg-accent/90 text-black font-semibold py-2.5 px-4 rounded-md transition-colors disabled:opacity-50"
            >
              {isLoading ? 'Entrando...' : 'Entrar'}
            </button>
          </form>

          {/* Sem VITE_GOOGLE_CLIENT_ID o bloco inteiro some: um botão que não
              teria como funcionar é pior do que botão nenhum. */}
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
            </>
          )}
        </div>
      </div>
    </div>
  );
};
